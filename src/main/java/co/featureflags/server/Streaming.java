package co.featureflags.server;

import co.featureflags.server.exterior.BasicConfig;
import co.featureflags.server.exterior.Context;
import co.featureflags.server.exterior.DataStorage;
import co.featureflags.server.exterior.DataStoreTypes;
import co.featureflags.server.exterior.HttpConfig;
import co.featureflags.server.exterior.UpdateProcessor;
import co.featureflags.server.exterior.Utils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import okhttp3.*;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class Streaming implements UpdateProcessor {

    //constants
    private static final String FULL_OPS = "full";
    private static final String PATCH_OPS = "patch";
    private final Integer NORMAL_CLOSE = 1000;
    private final String NORMAL_CLOSE_REASON = "normal close";
    private final Integer GOING_AWAY_CLOSE = 1001;
    private final String GOING_AWAY_CLOSE_REASON = "going away";
    private final Integer INVALID_REQUEST_CLOSE = 4003;
    private final String INVALID_REQUEST_CLOSE_REASON = "invalid request";
    private final Map<Integer, String> NOT_RECONN_CLOSE_REASON = ImmutableMap.of(NORMAL_CLOSE, NORMAL_CLOSE_REASON,
            GOING_AWAY_CLOSE, GOING_AWAY_CLOSE_REASON);
    private final List<Class<? extends Exception>> RECONNECT_EXCEPTIONS = ImmutableList.of(SocketTimeoutException.class,
            SocketException.class, EOFException.class);
    private final Duration PING_INTERVAL = Duration.ofSeconds(60);
    private final String DEFAULT_STREAMING_PATH = "/streaming";
    private final String AUTH_PARAMS = "?token=%s&type=server";
    private final Logger logger = Loggers.UPDATE_PROCESSOR;

    // final viariables
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean isWSConnected = new AtomicBoolean(false);
    private final AtomicInteger connCount = new AtomicInteger(0);
    private final ExecutorService storageUpdateExecutor = Executors.newFixedThreadPool(5);
    private final CompletableFuture<Boolean> initFuture = new CompletableFuture<>();
    private final StreamingWebSocketListener listener = new StreamingWebSocketListener();
    private final DataStorage storage;
    private final BasicConfig basicConfig;
    private final HttpConfig httpConfig;
    private final Integer maxRetryTimes;
    private final BackoffAndJitterStrategy strategy;
    private final String streamingURL;

    private OkHttpClient okHttpClient;
    private WebSocket webSocket;

    Streaming(DataStorage storage,
              Context config,
              String streamingURI,
              Duration firstRetryDelay,
              Integer maxRetryTimes) {
        this.storage = storage;
        this.basicConfig = config.basicConfig();
        this.httpConfig = config.http();
        this.streamingURL = StringUtils.stripEnd(streamingURI, "/").concat(DEFAULT_STREAMING_PATH);
        this.strategy = new BackoffAndJitterStrategy(firstRetryDelay);
        this.maxRetryTimes = (maxRetryTimes == null || maxRetryTimes <= 0) ? Integer.MAX_VALUE : maxRetryTimes;
    }

    @Override
    public Future<Boolean> start() {
        logger.info("Streaming Starting...");
        connCount.set(0);
        connect();
        return initFuture;
    }

    @Override
    public boolean isInitialized() {
        return storage.isInitialized() && initialized.get();
    }

    @Override
    public void close() {
        logger.info("Streaming is stopping...");
        if (okHttpClient != null && webSocket != null) {
            try {
                webSocket.close(GOING_AWAY_CLOSE, GOING_AWAY_CLOSE_REASON);
            } finally {
                storageUpdateExecutor.shutdown();
                okHttpClient.dispatcher().executorService().shutdown();
            }
        }
    }

    private void connect() {
        if (isWSConnected.get()) {
            logger.error("Streaming WebSocket is Connected");
            return;
        }
        int count = connCount.getAndIncrement();
        if (count >= maxRetryTimes) {
            logger.error("Streaming WebSocket have reached max retry");
            return;
        }
        okHttpClient = buildWebOkHttpClient();
        String token = Utils.buildToken(basicConfig.getEnvSecret());
        String url = String.format(streamingURL.concat(AUTH_PARAMS), token);
        Headers headers = Utils.headersBuilderFor(httpConfig).build();
        Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.headers(headers);
        requestBuilder.url(url);
        Request request = requestBuilder.build();
        logger.info("Streaming WebSocket is connecting...");
        strategy.setGoodRunAtNow();
        webSocket = okHttpClient.newWebSocket(request, listener);
    }

    private void reconnect(boolean forceToUseMaxRetryDelay) {
        Duration delay = strategy.nextDelay(forceToUseMaxRetryDelay);
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException ie) {
        } finally {
            connect();
        }
    }

    @NotNull
    private OkHttpClient buildWebOkHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(httpConfig.connectTime())
                .pingInterval(PING_INTERVAL)
                .retryOnConnectionFailure(false);
        Utils.buildProxyAndSocketFactoryFor(builder, httpConfig);
        OkHttpClient client = builder.build();
        return client;
    }

    private Runnable processDate(final DataModel.All allData) {
        return () -> {
            DataModel.Data data = allData.data();
            if (data != null && data.isValidated()) {
                String eventType = data.getEventType();
                Long version = data.getTimestamp();
                Map<DataStoreTypes.Category, Map<String, DataStoreTypes.Item>> updatedData = data.toStorageType();
                if (FULL_OPS.equalsIgnoreCase(eventType)) {
                    storage.init(updatedData, version);
                } else if (PATCH_OPS.equalsIgnoreCase(eventType)) {
                    for (Map.Entry<DataStoreTypes.Category, Map<String, DataStoreTypes.Item>> entry : updatedData.entrySet()) {
                        DataStoreTypes.Category category = entry.getKey();
                        for (Map.Entry<String, DataStoreTypes.Item> keyItem : entry.getValue().entrySet()) {
                            storage.upsert(category, keyItem.getKey(), keyItem.getValue(), version);
                        }
                    }
                }
                if (!initialized.getAndSet(true)) {
                    initFuture.complete(true);
                    logger.info("Initialized JAVA SDK client");
                }
            }
        };
    }

    private final class StreamingWebSocketListener extends WebSocketListener {

        @Override
        public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
            boolean isReconn = true;
            String message = NOT_RECONN_CLOSE_REASON.get(Integer.valueOf(code));
            if (message == null) {
                isReconn = false;
                message = StringUtils.isEmpty(reason) ? "unexpected close" : reason;
            }
            logger.info("Streaming WebSocket close reason: %s", message);
            isWSConnected.compareAndSet(true, false);
            if (isReconn) {
                reconnect(false);
            }
        }

        @Override
        public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
            logger.error("Streaming WebSocket Failure", t);
            isWSConnected.compareAndSet(true, false);
            boolean forceToUseMaxRetryDelay = false;
            boolean isReconn = false;
            Class tClass = t.getClass();
            for (Class cls : RECONNECT_EXCEPTIONS) {
                if (tClass == cls) {
                    isReconn = true;
                    if (tClass == EOFException.class) {
                        forceToUseMaxRetryDelay = true;
                    }
                }
            }
            if (isReconn) {
                reconnect(forceToUseMaxRetryDelay);
            }
        }

        @Override
        public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
            try {
                logger.info("Streaming WebSocket is processing data");
                DataModel.All allData = JsonHelper.deserialize(text, DataModel.All.class);
                storageUpdateExecutor.execute(processDate(allData));
            } catch (Exception ex) {
                logger.error("Streaming WebSocket ignore this message", ex);
            }
        }

        @Override
        public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
            logger.info("Ask data updating");
            isWSConnected.compareAndSet(false, true);
            String json;
            if (storage.isInitialized()) {
                Long timestamp = storage.getVersion();
                json = JsonHelper.serialize(new DataModel.DataSyncMessage(timestamp));
            } else {
                json = JsonHelper.serialize(new DataModel.DataSyncMessage(0L));
            }
            webSocket.send(json);
        }
    }

}
