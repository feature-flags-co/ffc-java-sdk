package co.featureflags.server.exterior;

import co.featureflags.server.Status;

public interface UpdateProcessorFactory {
    UpdateProcessor createUpdateProcessor(Context context, Status.DataUpdator dataUpdator);
}
