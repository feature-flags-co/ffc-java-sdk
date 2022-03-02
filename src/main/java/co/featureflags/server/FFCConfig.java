package co.featureflags.server;

import co.featureflags.server.exterior.DataStorageFactory;
import co.featureflags.server.exterior.HttpConfigFactory;
import co.featureflags.server.exterior.InsightProcessorFactory;
import co.featureflags.server.exterior.UpdateProcessorFactory;

import java.time.Duration;

/**
 * This class exposes advanced configuration options for the {@link co.featureflags.server.exterior.FFCClient}
 * Instances of this class must be constructed with a {@link co.featureflags.server.FFCConfig.Builder}.
 */
public class FFCConfig {
    static final String DEFAULT_BASE_URI = "https://api.feature-flags.co";
    static final String DEFAULT_STREAMING_URI = "wss://api.feature-flags.co";
    static final String DEFAULT_EVENTS_URI = "https://api.feature-flags.co";
    static final Duration DEFAULT_START_WAIT_TIME = Duration.ofSeconds(15);
    static final FFCConfig DEFAULT = new FFCConfig.Builder().build();

    private DataStorageFactory dataStorageFactory;
    private UpdateProcessorFactory updateProcessorFactory;
    private HttpConfigFactory httpConfigFactory;
    private InsightProcessorFactory insightProcessorFactory;

    private boolean offline;
    private Duration startWaitTime;

    private FFCConfig() {
        super();
    }

    public DataStorageFactory getDataStorageFactory() {
        return dataStorageFactory;
    }

    public UpdateProcessorFactory getUpdateProcessorFactory() {
        return updateProcessorFactory;
    }

    public HttpConfigFactory getHttpConfigFactory() {
        return httpConfigFactory;
    }

    public InsightProcessorFactory getInsightProcessorFactory() {
        return insightProcessorFactory;
    }

    public boolean isOffline() {
        return offline;
    }

    public Duration getStartWaitTime() {
        return startWaitTime;
    }

    public FFCConfig(Builder builder) {
        this.offline = builder.offline;
        this.startWaitTime = builder.startWaitTime == null ? DEFAULT_START_WAIT_TIME : builder.startWaitTime;
        if (builder.offline) {
            Loggers.CLIENT.info("JAVA SDK Client is in offline mode");
            this.updateProcessorFactory = Factory.externalOnlyDataUpdate();
            this.insightProcessorFactory = Factory.noInsightInOffline();
        } else {
            this.updateProcessorFactory =
                    builder.updateProcessorFactory == null ? Factory.streamingBuilder() : builder.updateProcessorFactory;
            this.insightProcessorFactory =
                    builder.insightProcessorFactory == null ? Factory.insightProcessorFactory() : builder.insightProcessorFactory;
        }
        this.dataStorageFactory =
                builder.dataStorageFactory == null ? Factory.inMemoryDataStorageFactory() : builder.dataStorageFactory;
        this.httpConfigFactory =
                builder.httpConfigFactory == null ? Factory.httpConfigFactory() : builder.httpConfigFactory;
    }

    /**
     * Builder to create advanced configuration options, calls can be chained.
     * <pre><code>
     *  FFCConfig config = new FFCConfig.Builder()
     *                     .startWaitTime(Duration.ZERO)
     *                     .offline(false)
     *                     .build()
     * </code></pre>
     */
    public static class Builder {

        private DataStorageFactory dataStorageFactory;
        private UpdateProcessorFactory updateProcessorFactory;
        private HttpConfigFactory httpConfigFactory;
        private InsightProcessorFactory insightProcessorFactory;
        private Duration startWaitTime;
        private boolean offline = false;

        public Builder() {
            super();
        }

        /**
         * Sets the implementation of the data storage to be used for holding feature flags and
         * related data received from LaunchDarkly, using a factory object.
         * The default is{@link Factory#inMemoryDataStorageFactory()}
         *
         * @param dataStorageFactory a {@link DataStorageFactory} instance
         * @return the builder
         */
        public Builder dataStorageFactory(DataStorageFactory dataStorageFactory) {
            this.dataStorageFactory = dataStorageFactory;
            return this;
        }

        /**
         * Sets the implementation of the {@link co.featureflags.server.exterior.UpdateProcessor} that receives feature flag data
         * from feature-flag.co, using a factory object. Depending on the implementation, the factory may be a builder that
         * allows you to set other configuration options as well.
         * The default is{@link Factory#streamingBuilder()}
         *
         * @param updateProcessorFactory an {@link UpdateProcessorFactory} instance
         * @return the builder
         */
        public Builder updateProcessorFactory(UpdateProcessorFactory updateProcessorFactory) {
            this.updateProcessorFactory = updateProcessorFactory;
            return this;
        }

        /**
         * Sets the SDK's networking configuration, using a factory object. Depending on the implementation,
         * the factory may be a builder that allows you to set other configuration options as well.
         * This object by defaut is a configuration builder obtained from {@link Factory#httpConfigFactory()},
         *
         * @param httpConfigFactory a {@link HttpConfigFactory}
         * @return the builder
         */
        public Builder httpConfigFactory(HttpConfigFactory httpConfigFactory) {
            this.httpConfigFactory = httpConfigFactory;
            return this;
        }

        /**
         * Sets the implementation of {@link co.featureflags.server.exterior.InsightProcessor} to be used for processing analytics events,
         * using a factory object. Depending on the implementation, the factory may be a builder that allows you to set other configuration options as well.
         * The default is{@link Factory#insightProcessorFactory()}
         *
         * @param insightProcessorFactory an {@link InsightProcessorFactory}
         * @return the builder
         */
        public Builder insightProcessorFactory(InsightProcessorFactory insightProcessorFactory) {
            this.insightProcessorFactory = insightProcessorFactory;
            return this;
        }

        /**
         * Set whether SDK is offline.
         *
         * @param offline when set to true no connection to feature-flag.co any more
         * @return the builder
         */
        public Builder offline(boolean offline) {
            this.offline = offline;
            return this;
        }

        /**
         * Set how long the constructor will block awaiting a successful data sync.
         * Setting this to a zero or negative duration will not block and cause the constructor to return immediately.
         *
         * @param startWaitTime maximum time to wait; null to use the default
         * @return the builder
         */
        public Builder startWaitTime(Duration startWaitTime) {
            this.startWaitTime = startWaitTime;
            return this;
        }

        /**
         * Builds the configured {@link FFCConfig}
         *
         * @return a {@link FFCConfig} instance
         */
        public FFCConfig build() {
            return new FFCConfig(this);
        }

    }


}
