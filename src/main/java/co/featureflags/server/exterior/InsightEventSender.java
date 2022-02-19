package co.featureflags.server.exterior;

import java.io.Closeable;

public interface InsightEventSender extends Closeable {
    void sendEvent(String eventUrl, String json);
}
