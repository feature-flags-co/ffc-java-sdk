package co.featureflags.server;

import co.featureflags.server.exterior.DataStorageFactory;
import co.featureflags.server.exterior.HttpConfigurationBuilder;
import co.featureflags.server.exterior.InsightProcessorFactory;
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

    public static UpdateProcessorFactory externalOnlyDataUpdate() {
        return FactoryImp.NullUpdateProcessorFactory.SINGLETON;
    }

    public static InsightProcessorFactory noInsightInOffline() {
        return FactoryImp.NullInsightProcessorFactory.SINGLETON;
    }

    public static InsightProcessorBuilder insightProcessorFactory() {
        return new FactoryImp.InsightProcessBuilderImpl();
    }

}
