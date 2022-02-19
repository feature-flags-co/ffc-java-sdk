package co.featureflags.server;

import co.featureflags.server.exterior.HttpConfig;
import co.featureflags.server.exterior.InsightEventSender;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

abstract class Senders {

    @NotNull
    private static OkHttpClient buildWebOkHttpClient(HttpConfig httpConfig) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(httpConfig.connectTime()).readTimeout(httpConfig.socketTime()).writeTimeout(httpConfig.socketTime()).retryOnConnectionFailure(false);
        Utils.buildProxyAndSocketFactoryFor(builder, httpConfig);
        return builder.build();
    }

    static class InsightEventSenderImp implements InsightEventSender {

        private static final MediaType JSON_CONTENT_TYPE = MediaType.parse("application/json; charset=utf-8");

        private final HttpConfig httpConfig;
        private final OkHttpClient okHttpClient;
        private final Integer maxRetryTimes;
        private final Duration retryInterval;

        public InsightEventSenderImp(HttpConfig httpConfig, Integer maxRetryTimes, Duration retryInterval) {
            this.httpConfig = httpConfig;
            this.maxRetryTimes = maxRetryTimes;
            this.retryInterval = retryInterval;
            this.okHttpClient = buildWebOkHttpClient(httpConfig);
        }

        @Override
        public void sendEvent(String eventUrl, String json) {
            Loggers.EVENTS.trace("events: {}", json);
            RequestBody body = RequestBody.create(json, JSON_CONTENT_TYPE);
            Headers headers = Utils.headersBuilderFor(httpConfig).build();
            Request request = new Request.Builder()
                    .headers(headers)
                    .url(eventUrl)
                    .post(body)
                    .build();

            Loggers.EVENTS.debug("Sending events...");
            for (int i = 0; i < maxRetryTimes + 1; i++) {
                if (i > 0) {
                    try {
                        Thread.sleep(retryInterval.toMillis());
                    } catch (InterruptedException ignore) {
                    }
                }

                try (Response response = okHttpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        Loggers.EVENTS.debug("sending events ok");
                        return;
                    }
                } catch (Exception ex) {
                    Loggers.EVENTS.error("events sending error: {}", ex.getMessage());
                }
            }
        }

        @Override
        public void close() {
            Utils.shutdownOKHttpClient("insight event sender", okHttpClient);
        }
    }
}
