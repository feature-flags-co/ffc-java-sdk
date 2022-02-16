package co.featureflags.server;

import co.featureflags.server.exterior.BasicConfig;
import co.featureflags.server.exterior.Context;
import co.featureflags.server.exterior.DataStorage;
import co.featureflags.server.exterior.DataStorageFactory;
import co.featureflags.server.exterior.DataStoreTypes;
import co.featureflags.server.exterior.HttpConfig;
import co.featureflags.server.exterior.HttpConfigurationBuilder;
import co.featureflags.server.exterior.UpdateProcessor;
import co.featureflags.server.exterior.UpdateProcessorFactory;
import co.featureflags.server.exterior.Utils;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

abstract class FactoryImp {
    static final class HttpConfigurationBuilderImpl extends HttpConfigurationBuilder {
        @Override
        public HttpConfig createHttpConfig(BasicConfig config) {
            connectTime = connectTime == null ? DEFAULT_CONN_TIME : connectTime;
            socketTime = socketTime == null ? DEFAULT_SOCK_TIME : socketTime;
            return new HttpConfingImpl(connectTime,
                    socketTime,
                    proxy,
                    authenticator,
                    socketFactory,
                    sslSocketFactory,
                    x509TrustManager,
                    Utils.defaultHeaders(config.getEnvSecret()));
        }
    }

    static final class StreamingBuilderImpl extends StreamingBuilder {
        @Override
        public UpdateProcessor createUpdateProcessor(Context config, Status.DataUpdator dataUpdator) {
            Loggers.UPDATE_PROCESSOR.info("Choose Streaming Update Processor");
            streamingURI = streamingURI == null ? DEFAULT_STREAMING_URI : streamingURI;
            firstRetryDelay = firstRetryDelay == null ? DEFAULT_FIRST_RETRY_DURATION : firstRetryDelay;
            return new Streaming(dataUpdator, config, streamingURI, firstRetryDelay, maxRetryTimes);
        }
    }

    static final class InMemoryDataStorageFactory implements DataStorageFactory {
        static final InMemoryDataStorageFactory SINGLETON = new InMemoryDataStorageFactory();

        @Override
        public DataStorage createDataStorage(Context config) {
            return new InMemoryDataStorage();
        }
    }

    static class NullDataStorageFactory implements DataStorageFactory {

        static final NullDataStorageFactory SINGLETON = new NullDataStorageFactory();

        @Override
        public DataStorage createDataStorage(Context config) {
            Loggers.CLIENT.info("Null Data Storage is only used for test");
            return NullDataStorage.SINGLETON;
        }
    }

    private static final class NullDataStorage implements DataStorage {

        static final NullDataStorage SINGLETON = new NullDataStorage();

        @Override
        public void init(Map<DataStoreTypes.Category, Map<String, DataStoreTypes.Item>> allData, Long version) {

        }

        @Override
        public DataStoreTypes.Item get(DataStoreTypes.Category category, String key) {
            return null;
        }

        @Override
        public Map<String, DataStoreTypes.Item> getAll(DataStoreTypes.Category category) {
            return null;
        }

        @Override
        public boolean upsert(DataStoreTypes.Category category, String key, DataStoreTypes.Item item, Long version) {
            return true;
        }

        @Override
        public boolean isInitialized() {
            return true;
        }

        @Override
        public long getVersion() {
            return 0;
        }

        @Override
        public void close() {
        }
    }

    static final class NullUpdateProcessorFactory implements UpdateProcessorFactory {

        static final NullUpdateProcessorFactory SINGLETON = new NullUpdateProcessorFactory();

        @Override
        public UpdateProcessor createUpdateProcessor(Context config, Status.DataUpdator dataUpdator) {
            if (config.basicConfig().isOffline()) {
                Loggers.CLIENT.info("SDK is in offline mode");
            } else {
                Loggers.CLIENT.info("SDK won't connect to feature-flag.co");
            }
            return new NullUpdateProcessor(dataUpdator);
        }
    }

    private static final class NullUpdateProcessor implements UpdateProcessor {

        private final Status.DataUpdator dataUpdator;

        NullUpdateProcessor(Status.DataUpdator dataUpdator) {
            this.dataUpdator = dataUpdator;
        }

        @Override
        public Future<Boolean> start() {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public boolean isInitialized() {
            return dataUpdator.storageInitialized();
        }

        @Override
        public void close() {

        }
    }

}
