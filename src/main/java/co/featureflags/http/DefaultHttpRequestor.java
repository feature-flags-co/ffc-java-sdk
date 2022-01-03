package co.featureflags.http;

import co.featureflags.FFCConfig;
import okhttp3.*;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class DefaultHttpRequestor {

    static final MediaType JSON
            = MediaType.get("application/json; charset=utf-8");

    static Map<String, String> DEFAULT_HEADERS;

    static {
        Map<String, String> map = new HashMap<>();
        map.put("Accept", "application/json");
        map.put("Content-Type", "application/json");
        DEFAULT_HEADERS = Collections.unmodifiableMap(map);
    }

    private final OkHttpClient httpClient;

    private final String baseUrl;

    public DefaultHttpRequestor(FFCConfig config) {
        this.baseUrl = config.getBaseUrl();
        OkHttpClient.Builder httpBuilder = new OkHttpClient.Builder();
        configureHttpClientBuilder(config, httpBuilder);
        httpClient = httpBuilder.build();
    }

    private void configureHttpClientBuilder(FFCConfig config, OkHttpClient.Builder builder) {
        builder.connectionPool(new ConnectionPool(5, 5, TimeUnit.SECONDS))
                .callTimeout(config.getTimeout(), TimeUnit.SECONDS)
                .retryOnConnectionFailure(false);
    }

    private URI concatenateUriPath(String baseUri, String path) {
        String addPath = path.startsWith("/") ? path.substring(1) : path;
        return URI.create(baseUri + (baseUri.endsWith("/") ? "" : "/") + addPath);
    }

    public String jsonPostDate(String path, String json) throws IOException, HttpErrorException {
        URL url = concatenateUriPath(this.baseUrl, path).toURL();
        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .headers(Headers.of(DEFAULT_HEADERS))
                .url(url)
                .post(body)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if(!response.isSuccessful())
                throw new HttpErrorException(response.code());
            return response.body().string();
        }
    }
}
