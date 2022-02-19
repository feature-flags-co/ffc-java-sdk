package co.featureflags.server.exterior;

public interface InsightEventSenderFactory {
    InsightEventSender createInsightEventSender(Context context);
}