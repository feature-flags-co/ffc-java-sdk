package co.featureflags.server;

import co.featureflags.commons.json.JsonHelper;
import co.featureflags.server.exterior.InsightProcessor;
import com.google.common.collect.Iterables;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
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
            flushScheduledExecutor.scheduleAtFixedRate(this::flush, config.getFlushInterval(), config.getFlushInterval(), TimeUnit.MILLISECONDS);
            Loggers.EVENTS.debug("insight processor is ready");
        }

        @Override
        public void send(InsightTypes.Event event) {
            if (!closed.get() && event != null) {
                if (event instanceof InsightTypes.FlagEvent) {
                    putEventAsync(InsightTypes.InsightMessageType.FLAGS, event);
                } else if (event instanceof InsightTypes.MetricEvent) {
                    putEventAsync(InsightTypes.InsightMessageType.METRICS, event);
                } else {
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
                msg.waitForComplete();
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

    private final static class FlushPayload {
        private final InsightTypes.Event[] events;

        public FlushPayload(InsightTypes.Event[] events) {
            this.events = events;
        }

        public InsightTypes.Event[] getEvents() {
            return events;
        }
    }

    private final static class EventBuffer {
        private final List<InsightTypes.Event> incomingEvents = new ArrayList<>();

        void add(InsightTypes.Event event) {
            incomingEvents.add(event);
        }

        FlushPayload getPayload() {
            return new FlushPayload(incomingEvents.toArray(new InsightTypes.Event[0]));
        }

        void clear() {
            incomingEvents.clear();
        }

        boolean isEmpty() {
            return incomingEvents.isEmpty();
        }

    }

    private final static class FlushPayloadRunner implements Runnable {

        private final static int MAX_EVENT_SIZE_PER_REQUEST = 50;

        private final InsightTypes.InsightConfig config;

        private final BlockingQueue<FlushPayload> payloadQueue;
        private final AtomicInteger busyFlushPaypladThreadNum;
        private final AtomicBoolean running;

        private final Thread thread;

        public FlushPayloadRunner(InsightTypes.InsightConfig config, BlockingQueue<FlushPayload> payloadQueue, AtomicInteger busyFlushPaypladThreadNum) {
            this.config = config;
            this.payloadQueue = payloadQueue;
            this.busyFlushPaypladThreadNum = busyFlushPaypladThreadNum;
            this.running = new AtomicBoolean(true);
            ThreadFactory threadFactory = Utils.createThreadFactory("flush-payload-worker-%d", true);
            this.thread = threadFactory.newThread(this);
            this.thread.start();
        }

        @Override
        public void run() {
            while (running.get()) {
                FlushPayload payload;
                try {
                    payload = payloadQueue.take(); // blocked until a payload comes in
                } catch (InterruptedException e) {
                    continue;
                }
                try {
                    // split the payload into small partitions and send them to featureflag.co
                    Iterables.partition(Arrays.asList(payload.getEvents()), MAX_EVENT_SIZE_PER_REQUEST)
                            .forEach(partition -> {
                                String json = JsonHelper.serialize(partition);
                                config.getSender().sendEvent(config.getEventUrl(), json);
                                Loggers.EVENTS.debug("paload size: {}", partition.size());
                            });
                } catch (Exception unexpected) {
                    Loggers.EVENTS.error("FFC JAVA SDK: unexpected error in sending payload: {}", unexpected.getMessage());
                }
                // busy payload worker - 1
                synchronized (busyFlushPaypladThreadNum) {
                    busyFlushPaypladThreadNum.decrementAndGet();
                    busyFlushPaypladThreadNum.notifyAll();
                }
            }
        }

        public void stop() {
            running.set(false);
            thread.interrupt();
            Loggers.EVENTS.debug("flush payload worker is stopping...");
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
        private final EventBuffer eventBuffer = new EventBuffer();
        // permits to flush events
        private final List<FlushPayloadRunner> flushWorkers;

        // This queue only holds one payload, that should be immediately picked up by any free flush worker.
        // if we try to push another one to this queue and then is refused,
        // it means all the flush workers are busy, this payload will be consumed until a flush worker becomes free again.
        // Events in the refused payload should be kept in buffer and try to be pushed to this queue in the next flush
        private final BlockingQueue<FlushPayload> payloadQueue = new ArrayBlockingQueue<>(1);

        public EventDispatcher(InsightTypes.InsightConfig config, BlockingQueue<InsightTypes.InsightMessage> inbox) {
            this.config = config;
            this.inbox = inbox;
            Thread mainThread = Utils.createThreadFactory("event-dispatcher", true).newThread(this::dispatchEvents);
            mainThread.start();
            this.flushWorkers = new ArrayList<>();
            for (int i = 0; i < MAX_FLUSH_WORKERS_NUMBER; i++) {
                FlushPayloadRunner task = new FlushPayloadRunner(config, payloadQueue, busyFlushPayloadThreadNum);
                flushWorkers.add(task);
            }

        }

        // blocks until a message is available and then:
        // 1: transfer the events to event buffer
        // 2: try to flush events to featureflag if a flush message arrives
        // 3: wait for releasing resources if a shutdown arrives
        private void dispatchEvents() {
            List<InsightTypes.InsightMessage> messages = new ArrayList<>();
            Loggers.EVENTS.debug("event dispatcher is working...");
            while (true) {
                try {
                    messages.clear();
                    messages.add(inbox.take());
                    inbox.drainTo(messages, BATCH_SIZE - 1);  // this nonblocking call allows us to pick up more messages if available
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
        }

        private void putEventToNextBuffer(InsightTypes.Event event) {
            if (closed.get()) {
                return;
            }
            if (event.isSendEvent()) {
                Loggers.EVENTS.debug("put event to buffer");
                eventBuffer.add(event);
            }

        }

        private void triggerFlush() {
            if (closed.get() || eventBuffer.isEmpty()) {
                return;
            }

            //get all the current events from event buffer
            FlushPayload payload = eventBuffer.getPayload();
            // busy payload worker + 1
            busyFlushPayloadThreadNum.incrementAndGet();
            if (payloadQueue.offer(payload)) {
                // put events to the next available flush worker, so drop them from our buffer
                eventBuffer.clear();
            } else {
                Loggers.EVENTS.debug("Skipped flushing because all workers are busy");
                // All the workers are busy so we can't flush now;
                // the buffer should keep the events for the next flush
                // busy payload worker - 1
                synchronized (busyFlushPayloadThreadNum) {
                    busyFlushPayloadThreadNum.decrementAndGet();
                    busyFlushPayloadThreadNum.notifyAll();
                }
            }
        }

        private void shutdown() {
            Loggers.EVENTS.debug("event dispatcher clean up threads and conn pool");
            try {
                // wait for all flush payload is well done
                waitUntilFlushPayLoadWorkerDown();
                //if buffer is not empty, flush it
//                if(!eventsBufferToNextFlush.isEmpty()){
//                    triggerFlush();
//                }
                // shutdown resources
                if (closed.compareAndSet(false, true)) {
                    for (FlushPayloadRunner task : flushWorkers) {
                        task.stop();
                    }
                    config.getSender().close();
                }
            } catch (Exception unexpected) {
                Loggers.EVENTS.error("FFC JAVA SDK: unexpected error when closing event dispatcher: {}", unexpected.getMessage());
            }
        }

    }

}
