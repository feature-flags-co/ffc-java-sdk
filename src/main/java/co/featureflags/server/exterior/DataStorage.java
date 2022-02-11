package co.featureflags.server.exterior;

import java.io.Closeable;
import java.util.Map;

public interface DataStorage extends Closeable {
    void init(Map<DataStoreTypes.Category, Map<String, DataStoreTypes.Item>> allData, Long version);

    DataStoreTypes.Item get(DataStoreTypes.Category category, String key);

    Map<String, DataStoreTypes.Item> getAll(DataStoreTypes.Category category);

    boolean upsert(DataStoreTypes.Category category,
                   String key,
                   DataStoreTypes.Item item,
                   Long version);

    boolean isInitialized();

    long getVersion();
}