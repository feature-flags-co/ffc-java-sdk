package co.featureflags.server.exterior;

import co.featureflags.server.Status;

/**
 * Interface for a factory that creates some implementation of {@link UpdateProcessor}.
 *
 * @see co.featureflags.server.Factory
 */
public interface UpdateProcessorFactory {
    /**
     * Creates an implementation instance.
     *
     * @param context     allows access to the client configuration
     * @param dataUpdator the {@link co.featureflags.server.Status.DataUpdator} which pushes data into the {@link DataStorage}
     * @return an {@link UpdateProcessor}
     */
    UpdateProcessor createUpdateProcessor(Context context, Status.DataUpdator dataUpdator);
}
