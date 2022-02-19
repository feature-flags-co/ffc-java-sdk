package co.featureflags.server.exterior;

import co.featureflags.server.InsightTypes;

import java.io.Closeable;

public interface InsightProcessor extends Closeable {
    void send(InsightTypes.Event event);

    void flush();
}
