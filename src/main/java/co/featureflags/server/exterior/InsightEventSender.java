package co.featureflags.server.exterior;

import co.featureflags.server.Factory;

import java.io.Closeable;

/**
 * interface for a component that can deliver preformatted event data.
 * <p>
 * The standard implementation is {@link Factory#insightProcessorFactory()}
 *
 * @see Factory
 */
public interface InsightEventSender extends Closeable {
    /**
     * deliver an event data payload.
     *
     * @param eventUrl the configured events endpoint url
     * @param json     the preformatted JSON data, as a string
     */
    void sendEvent(String eventUrl, String json);
}
