package co.featureflags.server.exterior;

public interface InsightProcessorFactory {

    InsightProcessor createInsightProcessor(Context context);

}
