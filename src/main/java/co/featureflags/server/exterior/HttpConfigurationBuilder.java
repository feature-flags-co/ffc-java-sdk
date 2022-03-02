package co.featureflags.server.exterior;

import co.featureflags.server.Utils;
import okhttp3.Authenticator;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import java.net.Proxy;
import java.time.Duration;


/**
 * Contains methods for configuring the SDK's networking behavior.
 * <p>
 * If you want to set non-default values, create a builder with {@link co.featureflags.server.Factory#httpConfigFactory()},
 * change its properties with the methods of this class and pass it to {@link co.featureflags.server.FFCConfig.Builder#httpConfigFactory(HttpConfigFactory)}:
 * <pre><code>
 *      FFCConfig config = new FFCConfig.Builder()
 *                     .httpConfigFactory(Factory.httpConfigFactory()
 *                             .connectTime(Duration.ofMillis(3000))
 *                             .httpProxy("my-proxy", 9000))
 *                     .build();
 * </code></pre>
 * <p>
 *
 * @see co.featureflags.server.Factory
 */
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

    /**
     * Sets the connection timeout. This is the time allowed for the SDK to make a socket connection to
     * any of API. The default value is 10s
     *
     * @param duration the connection timeout; null to use the default
     * @return the builder
     */
    public HttpConfigurationBuilder connectTime(Duration duration) {
        this.connectTime = (duration == null || duration.minusSeconds(1).isNegative()) ? DEFAULT_CONN_TIME : duration;
        return this;
    }

    /**
     * Sets the read and write timeout. This is the time allowed for the SDK to read/write
     * any of API. The default value is 15s
     *
     * @param duration the read/write timeout; null to use the default
     * @return the builder
     */
    public HttpConfigurationBuilder socketTime(Duration duration) {
        this.socketTime = (duration == null || duration.minusSeconds(1).isNegative()) ? DEFAULT_SOCK_TIME : duration;
        return this;
    }

    /**
     * Sets an HTTP proxy for making connections to feature-flag.co
     *
     * @param proxyHost the proxy hostname
     * @param proxyPort the proxy port
     * @return the builder
     */
    public HttpConfigurationBuilder httpProxy(String proxyHost, int proxyPort) {
        this.proxy = Utils.buildHTTPProxy(proxyHost, proxyPort);
        return this;
    }

    /**
     * Sets a user implementation proxy for making connections to feature-flag.co
     *
     * @param proxy the proxy
     * @return the builder
     */
    public HttpConfigurationBuilder proxy(Proxy proxy) {
        this.proxy = proxy;
        return this;
    }

    /**
     * Sets a basic authentication strategy for use with an HTTP proxy. This has no effect unless a proxy was specified
     *
     * @param userName a string
     * @param password a string
     * @return the builder
     */
    public HttpConfigurationBuilder passwordAuthenticator(String userName, String password) {
        this.authenticator = Utils.buildAuthenticator(userName, password);
        return this;
    }

    /**
     * Sets a user implementation authentication strategy for use with a proxy. This has no effect unless a proxy was specified
     *
     * @param authenticator the {@link Authenticator}
     * @return the builder
     */
    public HttpConfigurationBuilder authenticator(Authenticator authenticator) {
        this.authenticator = authenticator;
        return this;
    }

    /**
     * Specifies a custom socket configuration for HTTP connections to feature-flag.co.
     *
     * @param factory the socket factory
     * @return the builder
     */
    public HttpConfigurationBuilder socketFactory(SocketFactory factory) {
        this.socketFactory = factory;
        return this;
    }

    /**
     * Specifies a custom security configuration for HTTPS connections to feature-flag.co.
     *
     * @param factory      the SSL socket factory
     * @param trustManager the trust manager
     * @return the builder
     */
    public HttpConfigurationBuilder sslSocketFactory(SSLSocketFactory factory, X509TrustManager trustManager) {
        this.sslSocketFactory = factory;
        this.x509TrustManager = trustManager;
        return this;
    }
}
