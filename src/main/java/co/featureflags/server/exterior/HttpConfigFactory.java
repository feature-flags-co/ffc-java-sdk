package co.featureflags.server.exterior;

import co.featureflags.server.Factory;

/**
 * Interface for a factory that creates an {@link HttpConfig}.
 *
 * @see co.featureflags.server.Factory
 */
public interface HttpConfigFactory {
    /**
     * Creates the http configuration.
     *
     * @param config provides the basic SDK configuration properties
     * @return an {@link HttpConfig} instance
     */
    HttpConfig createHttpConfig(BasicConfig config);
}
