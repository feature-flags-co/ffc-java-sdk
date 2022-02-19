package co.featureflags.server;

import co.featureflags.server.exterior.HttpConfig;
import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public abstract class Utils {

    public static Iterable<Map.Entry<String, String>> defaultHeaders(String envSecret) {
        return ImmutableMap.of("envSecret", envSecret,
                        "User-Agent", "ffc-java-server-sdk4",
                        "Content-Type", "application/json")
                .entrySet();
    }

    public static ThreadFactory createThreadFactory(final String nameStyle,
                                                    final boolean isDaemon) {
        return new BasicThreadFactory.Builder()
                .namingPattern(nameStyle)
                .daemon(isDaemon)
                .build();
    }

    public static Proxy buildHTTPProxy(String proxyHost, int proxyPort) {
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
    }

    /**
     * See <a href="https://stackoverflow.com/questions/35554380/okhttpclient-proxy-authentication-how-to">proxy authentication</a>
     *
     * @param username username
     * @param password password
     * @return {@link Authenticator}
     */
    @Beta
    public static Authenticator buildAuthenticator(String username, String password) {
        return (route, response) -> {
            String credential = Credentials.basic(username, password);
            return response
                    .request()
                    .newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build();
        };
    }

    public static Headers.Builder headersBuilderFor(HttpConfig config) {
        Headers.Builder builder = new Headers.Builder();
        for (Map.Entry<String, String> kv : config.headers()) {
            builder.add(kv.getKey(), kv.getValue());
        }
        return builder;
    }

    public static void buildProxyAndSocketFactoryFor(OkHttpClient.Builder builder, HttpConfig httpConfig) {
        if (httpConfig.socketFactory() != null) {
            builder.socketFactory(httpConfig.socketFactory());
        }

        if (httpConfig.sslSocketFactory() != null) {
            if (httpConfig.trustManager() != null) {
                builder.sslSocketFactory(httpConfig.sslSocketFactory(), httpConfig.trustManager());
            } else {
                builder.sslSocketFactory(httpConfig.sslSocketFactory());
            }
        }

        if (httpConfig.proxy() != null) {
            builder.proxy(httpConfig.proxy());
            if (httpConfig.authenticator() != null) {
                builder.proxyAuthenticator(httpConfig.authenticator());
            }
        }
    }

    private static final Map<String, String> ALPHABETS =
            ImmutableMap.of("0", "Q",
                    "1", "B",
                    "2", "W",
                    "3", "S",
                    "4", "P",
                    "5", "H",
                    "6", "D",
                    "7", "X",
                    "8", "Z",
                    "9", "U");

    private static String encodeNumber(long number, int length) {
        String str = "000000000000" + number;
        String numberWithLeadingZeros = str.substring(str.length() - length);
        return new ArrayList<>(Arrays.asList(numberWithLeadingZeros.split("")))
                .stream().map(ALPHABETS::get).collect(Collectors.joining());

    }

    public static String buildToken(String envSecret) {
        String text = StringUtils.stripEnd(envSecret, "=");
        long now = Instant.now().toEpochMilli();
        String timestampCode = encodeNumber(now, String.valueOf(now).length());
        int start = Math.max((int) Math.floor(Math.random() * text.length()), 2);
        String part1 = encodeNumber(start, 3);
        String part2 = encodeNumber(timestampCode.length(), 2);
        String part3 = text.substring(0, start);
        String part4 = timestampCode;
        String part5 = text.substring(start);
        return String.format("%s%s%s%s%s", part1, part2, part3, part4, part5);
    }

    // https://square.github.io/okhttp/4.x/okhttp/okhttp3/-ok-http-client/#shutdown-isnt-necessary
    public static void shutdownOKHttpClient(String name, OkHttpClient client) {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
        if (client.cache() != null) {
            try {
                client.cache().close();
            } catch (Exception ignore) {
            }
        }
        Loggers.UTILS.debug("gracefully clean up okhttpclient in {}", name);
    }

    // https://ld246.com/article/1488023925829
    public static void shutDownThreadPool(String name, ThreadPoolExecutor pool, Duration timeout) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
        }
        Loggers.UTILS.debug("gracefully shut down thread pool of {}", name);
    }

}
