package co.featureflags.server.exterior;

import co.featureflags.server.Factory;
import co.featureflags.server.InsightTypes;

import java.io.Closeable;

/**
 * Interface for a component to send analytics events.
 * <p>
 * The standard implementations are:
 * <ul>
 * <li>{@link Factory#insightProcessorFactory()} (the default), which
 * sends events to featureflag.co
 * <li>{@link Factory#noInsightInOffline()} which does nothing
 * (on the assumption that another process will send the events);
 * </ul>
 *
 * @see Factory
 */
public interface InsightProcessor extends Closeable {

    /**
     * Records an event asynchronously.
     *
     * @param event
     */
    void send(InsightTypes.Event event);

    /**
     * Specifies that any buffered events should be sent as soon as possible, rather than waiting
     * for the next flush interval. This method is asynchronous, so events still may not be sent
     * until a later time. However, calling {@link Closeable#close()} will synchronously deliver
     * any events that were not yet delivered prior to shutting down.
     */
    void flush();
}
