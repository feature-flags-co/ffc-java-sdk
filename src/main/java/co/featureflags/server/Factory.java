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

    public static HttpConfigurationBuilder httpConfigFactory() {
        return new FactoryImp.HttpConfigurationBuilderImpl();
    }

    public static StreamingBuilder streamingBuilder() {
        return new FactoryImp.StreamingBuilderImpl();
    }

    public static DataStorageFactory inMemoryDataStorageFactory() {
        return FactoryImp.InMemoryDataStorageFactory.SINGLETON;
    }

    public static DataStorageFactory nullDataStorageFactory() {
        return FactoryImp.NullDataStorageFactory.SINGLETON;
    }

    public static UpdateProcessorFactory nullUpdateProcessorFactory() {
        return FactoryImp.NullUpdateProcessorFactory.SINGLETON;
    }

}
