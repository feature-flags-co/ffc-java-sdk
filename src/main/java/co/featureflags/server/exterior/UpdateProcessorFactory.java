package co.featureflags.server.exterior;

public interface UpdateProcessorFactory {
    UpdateProcessor createUpdateProcessor(Context config, DataStorage dataStorage);
}
