package co.featureflags.server.exterior;

import okhttp3.Authenticator;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import java.net.Proxy;
import java.time.Duration;
import java.util.Map;

public interface HttpConfig {

    Duration connectTime();

    /**
     * this param is not used in streaming
     *
     * @return read and write time out
     */
    Duration socketTime();

    Proxy proxy();

    Authenticator authenticator();

    SocketFactory socketFactory();

    SSLSocketFactory sslSocketFactory();

    X509TrustManager trustManager();

    Iterable<Map.Entry<String, String>> headers();
}
