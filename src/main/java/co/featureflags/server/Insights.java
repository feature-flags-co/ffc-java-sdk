package co.featureflags.server;

import co.featureflags.commons.json.JsonHelper;
import co.featureflags.server.exterior.InsightProcessor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static co.featureflags.server.InsightTypes.InsightMessageType.SHUTDOWN;

abstract class Insights {

    final static class InsightProcessorImpl implements InsightProcessor {

        private static final Duration AWAIT_TERMINATION = Duration.ofSeconds(2);

        private final BlockingQueue<InsightTypes.InsightMessage> inbox;

        private final ScheduledThreadPoolExecutor flushScheduledExecutor;

        private final AtomicBoolean closed = new AtomicBoolean(false);

        public InsightProcessorImpl(InsightTypes.InsightConfig config) {
            this.inbox = new ArrayBlockingQueue<>(config.capacity);
            new EventDispatcher(config, inbox);
            this.flushScheduledExecutor = new ScheduledThreadPoolExecutor(1, Utils.createThreadFactory("insight-periodic-flush-worker-%d", true));
            flushScheduledExecutor.scheduleAtFixedRate(this::flush, config.flushInterval, config.flushInterval, TimeUnit.MILLISECONDS);
            Loggers.EVENTS.debug("insight processor is ready");
        }

        @Override
        public void send(InsightTypes.Event event) {
            if (!closed.get() && event != null) {
                if (event instanceof InsightTypes.FlagEvent) {
                    putEventAsync(InsightTypes.InsightMessageType.FLAGS, event);
                } else if (event instanceof InsightTypes.MetricEvent) {
                    putEventAsync(InsightTypes.InsightMessageType.METRICS, event);
                } else{
                    Loggers.EVENTS.debug("ignore event type: {}", event.getClass().getName());
                }
            }
        }

        @Override
        public void flush() {
            if (!closed.get()) {
                putEventAsync(InsightTypes.InsightMessageType.FLUSH, null);
            }
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                Loggers.EVENTS.info("FFC JAVA SDK: insight processor is stopping");
                Utils.shutDownThreadPool("insight-periodic-flush-worker", flushScheduledExecutor, AWAIT_TERMINATION);
                //flush all the left events
                putEventAsync(InsightTypes.InsightMessageType.FLUSH, null);
                //shutdown, clear all the threads
                putEventAndWaitTermination(SHUTDOWN, null);
            }

        }

        private void putEventAsync(InsightTypes.InsightMessageType type, InsightTypes.Event event) {
            putMsgToInbox(new InsightTypes.InsightMessage(type, event, false));
        }

        private void putEventAndWaitTermination(InsightTypes.InsightMessageType type, InsightTypes.Event event) {
            InsightTypes.InsightMessage msg = new InsightTypes.InsightMessage(type, event, true);
            if (putMsgToInbox(msg)) {
                Loggers.EVENTS.debug("put {} WaitTermination message to inbox", type);
                msg.waitForComplete(Duration.ZERO);
            }
        }

        private boolean putMsgToInbox(InsightTypes.InsightMessage msg) {
            if (inbox.offer(msg)) {
                return true;
            }
            if (msg.getType() == SHUTDOWN) {
                while (true) {
                    try {
                        // must put the shut down to inbox;
                        inbox.put(msg);
                        return true;
                    } catch (InterruptedException ignore) {
                    }
                }
            }
            // if it reaches here, it means the application is probably doing tons of flag
            // evaluations across many threads-- so if we wait for a space in the inbox, we risk a very serious slowdown
            // of the app. To avoid that, we'll just drop the event or you can increase the capacity of inbox
            Loggers.EVENTS.warn("FFC JAVA SDK: events are being produced faster than they can be processed; some events will be dropped");
            return false;
        }

    }

    private final static class FlushPaypladRunner implements Runnable {

        private final InsightTypes.InsightConfig config;
        private final Semaphore permits;
        private final AtomicInteger busyFlushPaypladThreadNum;
        private final InsightTypes.Event[] payload;

        public FlushPaypladRunner(InsightTypes.InsightConfig config, Semaphore permits, AtomicInteger busyFlushPaypladThreadNum, InsightTypes.Event[] payload) {
            this.config = config;
            this.permits = permits;
            this.busyFlushPaypladThreadNum = busyFlushPaypladThreadNum;
            this.payload = payload;
        }

        @Override
        public void run() {
            try {
                String json = JsonHelper.serialize(payload);
                config.getSender().sendEvent(config.getEventUrl(), json);
            } catch (Exception unexpected) {
                Loggers.EVENTS.error("FFC JAVA SDK: unexpected error in sending payload: {}", unexpected.getMessage());
            }
            permits.release();
            synchronized (busyFlushPaypladThreadNum) {
                busyFlushPaypladThreadNum.decrementAndGet();
                busyFlushPaypladThreadNum.notifyAll();
            }
        }
    }

    private static final class EventDispatcher {
        private final static int MAX_QUEUE_SIZE = 20;
        private final static int MAX_FLUSH_WORKERS_NUMBER = 5;
        private final static Duration AWAIT_TERMINATION = Duration.ofSeconds(2);
        private final static int BATCH_SIZE = 50;
        private final BlockingQueue<InsightTypes.InsightMessage> inbox;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final AtomicInteger busyFlushPayloadThreadNum = new AtomicInteger(0);
        private final InsightTypes.InsightConfig config;
        private final ThreadPoolExecutor threadPoolExecutor;
        private final List<InsightTypes.Event> eventsBufferToNextFlush = new ArrayList<>();
        // permits to flush events
        private final Semaphore permits = new Semaphore(MAX_FLUSH_WORKERS_NUMBER);

        public EventDispatcher(InsightTypes.InsightConfig config, BlockingQueue<InsightTypes.InsightMessage> inbox) {
            this.config = config;
            this.inbox = inbox;
            this.threadPoolExecutor = new ThreadPoolExecutor(MAX_FLUSH_WORKERS_NUMBER,
                    MAX_FLUSH_WORKERS_NUMBER,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(MAX_QUEUE_SIZE),
                    Utils.createThreadFactory("flush-payload-worker-%d", true),
                    new ThreadPoolExecutor.CallerRunsPolicy());
            Thread mainThread = Utils.createThreadFactory("event-dispatcher", true).newThread(this::dispatchEvents);
            mainThread.start();

        }

        private void dispatchEvents() {
            List<InsightTypes.InsightMessage> messages = new ArrayList<>();
            Loggers.EVENTS.debug("event dispatcher is working...");
            while (true) {
                try {
                    messages.clear();
                    messages.add(inbox.take());
                    inbox.drainTo(messages, BATCH_SIZE - 1);
                    for (InsightTypes.InsightMessage message : messages) {
                        try {
                            switch (message.getType()) {
                                case FLAGS:
                                case METRICS:
                                    putEventToNextBuffer(message.getEvent());
                                    break;
                                case FLUSH:
                                    triggerFlush();
                                    break;
                                case SHUTDOWN:
                                    shutdown();
                                    message.completed();
                                    return;
                            }
                            message.completed();
                        } catch (Exception unexpected) {
                            Loggers.EVENTS.error("FFC JAVA SDK: unexpected error in event dispatcher {}", unexpected.getMessage());
                        }
                    }
                } catch (InterruptedException ignore) {
                } catch (Exception unexpected) {
                    Loggers.EVENTS.error("FFC JAVA SDK: unexpected error in event dispatcher {}", unexpected.getMessage());
                }
            }
        }

        private void waitUntilFlushPayLoadWorkerDown() {
            synchronized (busyFlushPayloadThreadNum) {
                while (busyFlushPayloadThreadNum.get() > 0) {
                    try {
                        busyFlushPayloadThreadNum.wait();
                    } catch (InterruptedException ignore) {
                    }
                }
            }
            Loggers.EVENTS.debug("flush payload worker is down");
        }

        private void putEventToNextBuffer(InsightTypes.Event event) {
            if (closed.get()) {
                return;
            }
            if (event.isSendEvent()) {
                Loggers.EVENTS.debug("put event to buffer");
                eventsBufferToNextFlush.add(event);
            }

        }

        private void triggerFlush() {
            if (closed.get() || eventsBufferToNextFlush.isEmpty()) {
                return;
            }
            InsightTypes.Event[] payload = eventsBufferToNextFlush.toArray(new InsightTypes.Event[0]);
            if (permits.tryAcquire()) {
                Loggers.EVENTS.debug("trigger flush");
                // busy payload worker + 1
                busyFlushPayloadThreadNum.incrementAndGet();
                // send events
                threadPoolExecutor.execute(new FlushPaypladRunner(config, permits, busyFlushPayloadThreadNum, payload));
                // clear buffer for next flush
                eventsBufferToNextFlush.clear();
            }
            // if no more space in the payload queue, the buffer will be merged in the next flush
        }

        private void shutdown() {
            Loggers.EVENTS.debug("event dispatcher clean up thread and conn pool");
            try {
                // wait for all flush payload is well done
                waitUntilFlushPayLoadWorkerDown();
                //if buffer is not empty, flush it
//                if(!eventsBufferToNextFlush.isEmpty()){
//                    triggerFlush();
//                }
                // shutdown resources
                if (closed.compareAndSet(false, true)) {
                    Utils.shutDownThreadPool("flush-payload-worker", threadPoolExecutor, AWAIT_TERMINATION);
                    config.getSender().close();
                }
            } catch (Exception unexpected) {
                Loggers.EVENTS.error("FFC JAVA SDK: unexpected error when closing event dispatcher: {}", unexpected.getMessage());
            }
        }

    }

}
