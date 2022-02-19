package co.featureflags.server.exterior;

import co.featureflags.server.Utils;
import okhttp3.Authenticator;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import java.net.Proxy;
import java.time.Duration;

public abstract class HttpConfigurationBuilder implements HttpConfigFactory {
    protected final Duration DEFAULT_CONN_TIME = Duration.ofSeconds(10);
    protected final Duration DEFAULT_SOCK_TIME = Duration.ofSeconds(15);
    protected Duration connectTime;
    protected Duration socketTime;
    protected Proxy proxy;
    protected Authenticator authenticator;
    protected SocketFactory socketFactory;
    protected SSLSocketFactory sslSocketFactory;
    protected X509TrustManager x509TrustManager;

    public HttpConfigurationBuilder connectTime(Duration duration) {
        this.connectTime = (duration == null || duration.minusSeconds(1).isNegative()) ? DEFAULT_CONN_TIME : duration;
        return this;
    }

    public HttpConfigurationBuilder socketTime(Duration duration) {
        this.socketTime = (duration == null || duration.minusSeconds(1).isNegative()) ? DEFAULT_SOCK_TIME : duration;
        return this;
    }

    public HttpConfigurationBuilder httpProxy(String proxyHost, int proxyPort) {
        this.proxy = Utils.buildHTTPProxy(proxyHost, proxyPort);
        return this;
    }

    public HttpConfigurationBuilder proxy(Proxy proxy) {
        this.proxy = proxy;
        return this;
    }

    public HttpConfigurationBuilder passwordAuthenticator(String userName, String password) {
        this.authenticator = Utils.buildAuthenticator(userName, password);
        return this;
    }

    public HttpConfigurationBuilder authenticator(Authenticator authenticator) {
        this.authenticator = authenticator;
        return this;
    }

    public HttpConfigurationBuilder socketFactory(SocketFactory factory) {
        this.socketFactory = factory;
        return this;
    }

    public HttpConfigurationBuilder sslSocketFactory(SSLSocketFactory factory) {
        this.sslSocketFactory = factory;
        return this;
    }

    public HttpConfigurationBuilder trustManager(X509TrustManager trustManager) {
        this.x509TrustManager = trustManager;
        return this;
    }
}
