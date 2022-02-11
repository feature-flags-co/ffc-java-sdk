package co.featureflags.server.exterior;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.Headers;
import okhttp3.OkHttpClient;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public abstract class Utils {

    public static Iterable<Map.Entry<String, String>> defaultHeaders(String envSecret) {
        return ImmutableMap.of("envSecret", envSecret,
                        "User-Agent", "ffc-java-server-sdk4",
                        "Content-Type", "application/json")
                .entrySet();
    }

    public static ThreadFactory createThreadFactory(final String name,
                                                    final boolean isDaemon,
                                                    final Integer threadPriority) {
        return runnable -> {
            Thread t = Executors.defaultThreadFactory().newThread(runnable);
            t.setDaemon(isDaemon);
            t.setPriority(threadPriority);
            t.setName(name);
            return t;
        };
    }

    public static Proxy buildHTTPProxy(String proxyHost, int proxyPort) {
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
    }

    /**
     * See {@linktourl https://stackoverflow.com/questions/35554380/okhttpclient-proxy-authentication-how-to}
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

    public static String buildToken(String envSecret) {
        return envSecret;
    }

}
