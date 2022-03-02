package co.featureflags.server.exterior;

/**
 * Interface for a factory that creates an implementation of {@link InsightEventSender}.
 *
 * @see co.featureflags.server.Factory
 */
public interface InsightEventSenderFactory {
    /**
     * create an implementation of {@link InsightEventSender}.
     *
     * @param context allows access to the client configuration
     * @return an {@link InsightEventSender}
     */
    InsightEventSender createInsightEventSender(Context context);
}