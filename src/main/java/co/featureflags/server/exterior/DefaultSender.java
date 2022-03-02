package co.featureflags.server.exterior;

import java.io.Closeable;

/**
 * interface for the http connection to saas API to send or receive the details of feature flags, user segments, events etc.
 */
public interface DefaultSender extends Closeable {
    /**
     * send the json objects to saas API in the post method
     *
     * @param url      the url to send json
     * @param jsonBody json string
     * @return a response of saas API
     */
    String postJson(String url, String jsonBody);
}
