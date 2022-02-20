package co.featureflags.server;

import co.featureflags.commons.json.JsonHelper;
import co.featureflags.commons.json.JsonParseException;
import co.featureflags.server.exterior.BasicConfig;
import co.featureflags.server.exterior.Context;
import co.featureflags.server.exterior.DataStoreTypes;
import co.featureflags.server.exterior.HttpConfig;
import co.featureflags.server.exterior.UpdateProcessor;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.EOFException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static co.featureflags.server.Status.DATA_INVALID_ERROR;
import static co.featureflags.server.Status.NETWORK_ERROR;
import static co.featureflags.server.Status.REQUEST_INVALID_ERROR;
import static co.featureflags.server.Status.RUNTIME_ERROR;
import static co.featureflags.server.Status.UNKNOWN_CLOSE_CODE;
import static co.featureflags.server.Status.UNKNOWN_ERROR;

final class Streaming implements UpdateProcessor {

    //constants
    private static final String FULL_OPS = "full";
    private static final String PATCH_OPS = "patch";
    private static final Integer NORMAL_CLOSE = 1000;
    private static final String NORMAL_CLOSE_REASON = "normal close";
    private static final Integer INVALID_REQUEST_CLOSE = 4003;
    private static final String INVALID_REQUEST_CLOSE_REASON = "invalid request";
    private static final Integer GOING_AWAY_CLOSE = 1001;
    private static final String JUST_RECONN_REASON_REGISTERED = "reconn";
    private static final int MAX_QUEUE_SIZE = 20;
    private static final Duration PING_INTERVAL = Duration.ofSeconds(20);
    private static final Duration AWAIT_TERMINATION = Duration.ofSeconds(2);
    private static final String DEFAULT_STREAMING_PATH = "/streaming";
    private static final String AUTH_PARAMS = "?token=%s&type=server";
    private static final Map<Integer, String> NOT_RECONN_CLOSE_REASON = ImmutableMap.of(NORMAL_CLOSE, NORMAL_CLOSE_REASON, INVALID_REQUEST_CLOSE, INVALID_REQUEST_CLOSE_REASON);
    private static final List<Class<? extends Exception>> RECONNECT_EXCEPTIONS = ImmutableList.of(SocketTimeoutException.class, SocketException.class, EOFException.class);
    private static final Logger logger = Loggers.UPDATE_PROCESSOR;

    // final viariables
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean isWSConnected = new AtomicBoolean(false);
    private final AtomicInteger connCount = new AtomicInteger(0);
    private final CompletableFuture<Boolean> initFuture = new CompletableFuture<>();
    private final StreamingWebSocketListener listener = new DefaultWebSocketListener();
    private final ThreadPoolExecutor storageUpdateExecutor;
    private final Status.DataUpdator updator;
    private final BasicConfig basicConfig;
    private final HttpConfig httpConfig;
    private final Integer maxRetryTimes;
    private final BackoffAndJitterStrategy strategy;
    private final String streamingURL;

    private final Semaphore permits = new Semaphore(MAX_QUEUE_SIZE);

    private final OkHttpClient okHttpClient;
    WebSocket webSocket;

    Streaming(Status.DataUpdator updator, Context config, String streamingURI, Duration firstRetryDelay, Integer maxRetryTimes) {
        this.updator = updator;
        this.basicConfig = config.basicConfig();
        this.httpConfig = config.http();
        this.streamingURL = StringUtils.stripEnd(streamingURI, "/").concat(DEFAULT_STREAMING_PATH);
        this.strategy = new BackoffAndJitterStrategy(firstRetryDelay);
        this.maxRetryTimes = (maxRetryTimes == null || maxRetryTimes <= 0) ? Integer.MAX_VALUE : maxRetryTimes;
        this.okHttpClient = buildWebOkHttpClient();

        //be sure of FIFO;
        //the data sync is based on timestamp and versioned data
        // if two many updates exceeds max num of wait queue, run in the main thread
        this.storageUpdateExecutor = new ThreadPoolExecutor(1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(MAX_QUEUE_SIZE),
                Utils.createThreadFactory("data-sync-worker-%d", true),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @Override
    public Future<Boolean> start() {
        logger.info("Streaming Starting...");
        // flags reset to original state
        connCount.set(0);
        isWSConnected.set(false);
        connect();
        return initFuture;
    }

    @Override
    public boolean isInitialized() {
        return updator.storageInitialized() && initialized.get();
    }

    @Override
    public void close() {
        logger.info("Streaming is stopping...");
        if (webSocket != null) {
            webSocket.close(NORMAL_CLOSE, NORMAL_CLOSE_REASON);
        }
    }

    private void clearExecutor() {
        Loggers.UPDATE_PROCESSOR.debug("streaming processor clean up thread and conn pool");
        Utils.shutDownThreadPool("Streaming", storageUpdateExecutor, AWAIT_TERMINATION);
        Utils.shutdownOKHttpClient("Streaming", okHttpClient);
    }

    private void connect() {
        if (isWSConnected.get()) {
            logger.error("Streaming WebSocket is already Connected");
            return;
        }
        int count = connCount.getAndIncrement();
        if (count >= maxRetryTimes) {
            logger.error("Streaming WebSocket have reached max retry");
            return;
        }

        String token = Utils.buildToken(basicConfig.getEnvSecret());
        String url = String.format(streamingURL.concat(AUTH_PARAMS), token);
        Headers headers = Utils.headersBuilderFor(httpConfig).build();
        Request request = new Request.Builder()
                .headers(headers)
                .url(url)
                .build();
        logger.info("Streaming WebSocket is connecting...");
        strategy.setGoodRunAtNow();
        webSocket = okHttpClient.newWebSocket(request, listener);
    }

    private void reconnect(boolean forceToUseMaxRetryDelay) {
        try {
            Duration delay = strategy.nextDelay(forceToUseMaxRetryDelay);
            long delayInMillis = delay.toMillis();
            logger.info("Streaming WebSocket will reconnect in {} milliseconds", delayInMillis);
            Thread.sleep(delayInMillis);
        } catch (InterruptedException ie) {
            logger.warn("unexpected interruption {}", ie.getMessage());
        } finally {
            connect();
        }
    }

    @NotNull
    private OkHttpClient buildWebOkHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(httpConfig.connectTime()).pingInterval(PING_INTERVAL).retryOnConnectionFailure(false);
        Utils.buildProxyAndSocketFactoryFor(builder, httpConfig);
        return builder.build();
    }

    private Callable<Boolean> processDateAsync(final DataModel.Data data) {
        return () -> {
            boolean opOK = false;
            String eventType = data.getEventType();
            Long version = data.getTimestamp();
            Map<DataStoreTypes.Category, Map<String, DataStoreTypes.Item>> updatedData = data.toStorageType();
            if (FULL_OPS.equalsIgnoreCase(eventType)) {
                boolean fullOK = updator.init(updatedData, version);
                opOK = fullOK;
            } else if (PATCH_OPS.equalsIgnoreCase(eventType)) {
                // streaming patch is a real time update
                // patch data contains only one item in just one category.
                // no data update is considered as a good operation
                boolean patchOK = true;
                for (Map.Entry<DataStoreTypes.Category, Map<String, DataStoreTypes.Item>> entry : updatedData.entrySet()) {
                    DataStoreTypes.Category category = entry.getKey();
                    for (Map.Entry<String, DataStoreTypes.Item> keyItem : entry.getValue().entrySet()) {
                        patchOK = updator.upsert(category, keyItem.getKey(), keyItem.getValue(), version);
                    }
                }
                opOK = patchOK;
            }
            if (opOK) {
                if (initialized.compareAndSet(false, true)) {
                    initFuture.complete(true);
                }
                logger.info("processing data is well done");
                updator.updateStatus(Status.StateType.OK, null);
            } else {
                // reconnect to server to get back data after data storage failed
                // the reason is gathered by DataUpdator
                // close code 1001 means peer going away
                webSocket.close(GOING_AWAY_CLOSE, JUST_RECONN_REASON_REGISTERED);
            }
            permits.release();
            return opOK;
        };
    }

    private final class DefaultWebSocketListener extends StreamingWebSocketListener {
        // this callback method may throw a JsonParseException
        // if received data is invalid
        @Override
        public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
            logger.info("Streaming WebSocket is processing data");
            DataModel.All all = JsonHelper.deserialize(text, DataModel.All.class);
            if (all.isProcessData()) {
                try {
                    permits.acquire();
                    storageUpdateExecutor.submit(processDateAsync(all.data()));
                } catch (InterruptedException ignore) {
                }
            }
        }

        @Override
        public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
            super.onOpen(webSocket, response);
            String json;
            if (updator.storageInitialized()) {
                Long timestamp = updator.getVersion();
                json = JsonHelper.serialize(new DataModel.DataSyncMessage(timestamp));
            } else {
                json = JsonHelper.serialize(new DataModel.DataSyncMessage(0L));
            }
            webSocket.send(json);
        }
    }


    abstract class StreamingWebSocketListener extends WebSocketListener {

        @Override
        public final void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
            boolean isReconn = false;
            // close conn if the code is 1000 or 4003
            // any other close code will cause a reconnecting to server
            String message = NOT_RECONN_CLOSE_REASON.get(code);
            if (message == null) {
                isReconn = true;
                message = StringUtils.isEmpty(reason) ? "unexpected close" : reason;
            }
            logger.info("Streaming WebSocket close reason: {}", message);
            isWSConnected.compareAndSet(true, false);

            if (isReconn) {
                // if code is not 1001, it's a unknown close code received by server
                if (!JUST_RECONN_REASON_REGISTERED.equals(reason)) {
                    updator.updateStatus(Status.StateType.INTERRUPTED, Status.ErrorInfo.of(UNKNOWN_CLOSE_CODE, reason));
                }
                reconnect(false);
            } else {
                // authorization error
                if (code == INVALID_REQUEST_CLOSE) {
                    updator.updateStatus(Status.StateType.OFF, Status.ErrorInfo.of(REQUEST_INVALID_ERROR, reason));
                } else {
                    // normal close by client peer
                    updator.updateStatus(Status.StateType.OFF, null);
                }
                // clean up thread and conn pool
                clearExecutor();
            }
        }

        @Override
        public final void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
            logger.error("Streaming WebSocket Failure", t);
            isWSConnected.compareAndSet(true, false);
            boolean forceToUseMaxRetryDelay = false;
            boolean isReconn = false;
            String errorType = null;
            Class<? extends Throwable> tClass = t.getClass();
            // runtime exception restart except JsonParseException
            if (t instanceof RuntimeException) {
                isReconn = tClass != JsonParseException.class;
                errorType = isReconn ? RUNTIME_ERROR : DATA_INVALID_ERROR;
            } else {
                // restart a cause of network error
                for (Class<? extends Exception> cls : RECONNECT_EXCEPTIONS) {
                    if (tClass == cls) {
                        isReconn = true;
                        errorType = NETWORK_ERROR;
                        // maybe kicked off by server side
                        if (tClass == EOFException.class) {
                            forceToUseMaxRetryDelay = true;
                        }
                    }
                }
                if (errorType == null) {
                    errorType = UNKNOWN_ERROR;
                }
            }
            Status.ErrorInfo errorInfo = Status.ErrorInfo.of(errorType, t.getMessage());
            if (isReconn) {
                updator.updateStatus(Status.StateType.INTERRUPTED, errorInfo);
                reconnect(forceToUseMaxRetryDelay);
            } else {
                updator.updateStatus(Status.StateType.OFF, errorInfo);
                // clean up thread and conn pool
                clearExecutor();
            }
        }

        @Override
        public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
            logger.info("Ask Data Updating, http code {}", response.code());
            isWSConnected.compareAndSet(false, true);
        }
    }
}
