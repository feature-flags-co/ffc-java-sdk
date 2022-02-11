package co.featureflags.server;

import co.featureflags.server.exterior.HttpConfig;
import okhttp3.Authenticator;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import java.net.Proxy;
import java.time.Duration;
import java.util.Map;

final class HttpConfingImpl implements HttpConfig {
    private final Duration connectTime;
    private final Duration socketTime;
    private final Proxy proxy;
    private final Authenticator authenticator;
    private final SocketFactory socketFactory;
    private final SSLSocketFactory sslSocketFactory;
    private final X509TrustManager x509TrustManager;
    private final Iterable<Map.Entry<String, String>> headers;

    HttpConfingImpl(Duration connectTime,
                    Duration socketTime,
                    Proxy proxy,
                    Authenticator authenticator,
                    SocketFactory socketFactory,
                    SSLSocketFactory sslSocketFactory,
                    X509TrustManager x509TrustManager,
                    Iterable<Map.Entry<String, String>> headers) {
        this.connectTime = connectTime;
        this.socketTime = socketTime;
        this.proxy = proxy;
        this.authenticator = authenticator;
        this.socketFactory = socketFactory;
        this.sslSocketFactory = sslSocketFactory;
        this.x509TrustManager = x509TrustManager;
        this.headers = headers;
    }

    @Override
    public Duration connectTime() {
        return connectTime;
    }

    @Override
    public Duration socketTime() {
        return socketTime;
    }

    @Override
    public Proxy proxy() {
        return proxy;
    }

    @Override
    public Authenticator authenticator() {
        return authenticator;
    }

    @Override
    public SocketFactory socketFactory() {
        return socketFactory;
    }

    @Override
    public SSLSocketFactory sslSocketFactory() {
        return sslSocketFactory;
    }

    @Override
    public X509TrustManager trustManager() {
        return x509TrustManager;
    }

    @Override
    public Iterable<Map.Entry<String, String>> headers() {
        return headers;
    }
}
