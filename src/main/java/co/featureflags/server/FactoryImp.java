package co.featureflags.server;

import co.featureflags.server.exterior.BasicConfig;
import co.featureflags.server.exterior.Context;
import co.featureflags.server.exterior.DataStorage;
import co.featureflags.server.exterior.DataStorageFactory;
import co.featureflags.server.exterior.HttpConfig;
import co.featureflags.server.exterior.HttpConfigurationBuilder;
import co.featureflags.server.exterior.StreamingBuilder;
import co.featureflags.server.exterior.UpdateProcessor;
import co.featureflags.server.exterior.Utils;

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
        public UpdateProcessor createUpdateProcessor(Context config, DataStorage dataStorage) {
            Loggers.UPDATE_PROCESSOR.info("Choose Streaming Update Processor");
            streamingURI = streamingURI == null ? DEFAULT_STREAMING_URI : streamingURI;
            firstRetryDelay = firstRetryDelay == null ? DEFAULT_FIRST_RETRY_DURATION : firstRetryDelay;
            return new Streaming(dataStorage, config, streamingURI, firstRetryDelay, maxRetryTimes);
        }
    }

    static final class InMemoryDataStorageFactory implements DataStorageFactory {
        static final InMemoryDataStorageFactory SINGLETON = new InMemoryDataStorageFactory();

        @Override
        public DataStorage createDataStorage(Context config) {
            return new InMemoryDataStorage();
        }
    }

}
