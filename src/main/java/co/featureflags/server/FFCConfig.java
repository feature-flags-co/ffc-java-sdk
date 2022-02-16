package co.featureflags.server;

import co.featureflags.server.exterior.DataStorageFactory;
import co.featureflags.server.exterior.HttpConfigFactory;
import co.featureflags.server.exterior.UpdateProcessorFactory;

import java.time.Duration;

public class FFCConfig {
    static final String DEFAULT_BASE_URI = "https://api.feature-flags.co";
    static final String DEFAULT_STREAMING_URI = "wss://api.feature-flags.co";
    static final String DEFAULT_EVENTS_URI = "https://api.feature-flags.co";
    static final Duration DEFAULT_START_WAIT_TIME = Duration.ofSeconds(15);
    static final FFCConfig DEFAULT = new FFCConfig.Builder().build();

    private DataStorageFactory dataStorageFactory;
    private UpdateProcessorFactory updateProcessorFactory;
    private HttpConfigFactory httpConfigFactory;

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
        } else {
            this.updateProcessorFactory =
                    builder.updateProcessorFactory == null ? Factory.streamingBuilder() : builder.updateProcessorFactory;
        }
        this.dataStorageFactory =
                builder.dataStorageFactory == null ? Factory.inMemoryDataStorageFactory() : builder.dataStorageFactory;
        this.httpConfigFactory =
                builder.httpConfigFactory == null ? Factory.httpConfigFactory() : builder.httpConfigFactory;
    }

    public static class Builder {

        private DataStorageFactory dataStorageFactory;
        private UpdateProcessorFactory updateProcessorFactory;
        private HttpConfigFactory httpConfigFactory;
        private Duration startWaitTime;
        private boolean offline = false;

        public Builder() {
            super();
        }

        public Builder dataStorageFactory(DataStorageFactory dataStorageFactory) {
            this.dataStorageFactory = dataStorageFactory;
            return this;
        }

        public Builder updateProcessorFactory(UpdateProcessorFactory updateProcessorFactory) {
            this.updateProcessorFactory = updateProcessorFactory;
            return this;
        }

        public Builder httpConfigFactory(HttpConfigFactory httpConfigFactory) {
            this.httpConfigFactory = httpConfigFactory;
            return this;
        }

        public Builder offline(boolean offline) {
            this.offline = offline;
            return this;
        }

        public Builder startWaitTime(Duration startWaitTime) {
            this.startWaitTime = startWaitTime;
            return this;
        }

        public FFCConfig build() {
            return new FFCConfig(this);
        }

    }


}
