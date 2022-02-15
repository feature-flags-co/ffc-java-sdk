package co.featureflags.server.exterior;

public interface DataStorageFactory {
    DataStorage createDataStorage(Context context);
}
