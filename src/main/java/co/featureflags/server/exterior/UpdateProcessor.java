package co.featureflags.server.exterior;

import java.io.Closeable;
import java.util.concurrent.Future;

public interface UpdateProcessor extends Closeable {

    Future<Boolean> start();

    boolean isInitialized();
}
