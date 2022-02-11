package co.featureflags.server;

import co.featureflags.server.exterior.DataStorageFactory;
import co.featureflags.server.exterior.HttpConfigFactory;
import co.featureflags.server.exterior.HttpConfigurationBuilder;
import co.featureflags.server.exterior.UpdateProcessor;
import co.featureflags.server.exterior.UpdateProcessorFactory;

public abstract class Factory {

    private Factory() {
        super();
    }

    public static HttpConfigFactory httpConfigFactory() {
        return new FactoryImp.HttpConfigurationBuilderImpl();
    }

    public static UpdateProcessorFactory streamingBuilder() {
        return new FactoryImp.StreamingBuilderImpl();
    }

    public static DataStorageFactory inMemoryDataStorageFactory() {
        return FactoryImp.InMemoryDataStorageFactory.SINGLETON;
    }
}
