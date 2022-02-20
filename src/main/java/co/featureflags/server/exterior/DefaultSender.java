package co.featureflags.server.exterior;

import java.io.Closeable;

public interface DefaultSender extends Closeable {
    String postJson(String url, String jsonBody);
}
