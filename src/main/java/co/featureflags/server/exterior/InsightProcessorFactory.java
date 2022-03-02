package co.featureflags.server.exterior;

/**
 * Interface for a factory that creates an implementation of {@link InsightProcessor}.
 *
 * @see co.featureflags.server.Factory
 */

public interface InsightProcessorFactory {

    /**
     * creates an implementation of {@link InsightProcessor}
     *
     * @param context allows access to the client configuration
     * @return an {@link InsightProcessor}
     */
    InsightProcessor createInsightProcessor(Context context);

}
