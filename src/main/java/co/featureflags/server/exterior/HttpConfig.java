package co.featureflags.server.exterior;

import okhttp3.Authenticator;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import java.net.Proxy;
import java.time.Duration;
import java.util.Map;

/**
 * Interface to encapsulate top-level HTTP configuration that applies to all SDK components.
 * <p>
 * Use {@link HttpConfigurationBuilder} to construct an instance
 * <p>
 * The SDK's built-in components use OkHttp as the HTTP client implementation, but since OkHttp types
 * are not surfaced in the public API and custom components might use some other implementation, this
 * class only provides the properties that would be used to create an HTTP client.
 */
public interface HttpConfig {

    /**
     * The connection timeout. This is the time allowed for the HTTP client to connect
     * to the server.
     *
     * @return the connection timeout;
     */
    Duration connectTime();

    /**
     * this param is not used in streaming
     *
     * @return read and write time out
     */
    Duration socketTime();

    /**
     * The proxy configuration, if any.
     *
     * @return a {@link Proxy} instance or null
     */
    Proxy proxy();

    /**
     * The authentication method to use for a proxy, if any. Ignored if {@link #proxy()} is null.
     *
     * @return an {@link Authenticator} implementation or null
     */
    Authenticator authenticator();

    /**
     * The configured socket factory for insecure connections.
     *
     * @return a SocketFactory or null
     */
    SocketFactory socketFactory();

    /**
     * The configured socket factory for secure connections.
     *
     * @return a SSLSocketFactory or null
     */
    SSLSocketFactory sslSocketFactory();

    /**
     * The configured trust manager for secure connections, if custom certificate verification is needed.
     *
     * @return an X509TrustManager or null
     */
    X509TrustManager trustManager();

    /**
     * Returns the basic headers that should be added to all HTTP requests from SDK components to
     * feature-flag.co API, based on the current SDK configuration.
     *
     * @return a list of HTTP header names and values
     */

    Iterable<Map.Entry<String, String>> headers();
}
