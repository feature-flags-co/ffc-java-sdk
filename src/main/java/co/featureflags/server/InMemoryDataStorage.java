package co.featureflags.server;

import co.featureflags.server.exterior.DataStorage;
import co.featureflags.server.exterior.DataStoreTypes;
import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * A thread-safe, versioned storage for feature flags and related data based on a
 * {@link HashMap}. This is the default implementation of {@link DataStorage}.
 */

final class InMemoryDataStorage implements DataStorage {
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private volatile boolean initialized = false;
    private volatile Map<DataStoreTypes.Category, Map<String, DataStoreTypes.Item>> allData = ImmutableMap.of();
    private volatile long version = 0;

    InMemoryDataStorage() {
        super();
    }

    @Override
    public void init(Map<DataStoreTypes.Category, Map<String, DataStoreTypes.Item>> allData, Long version) {
        if (version == null || this.version >= version || allData == null || allData.isEmpty()) {
            return;
        }

        rwLock.writeLock().lock();
        try {
            this.allData = ImmutableMap.copyOf(allData);
            initialized = true;
            this.version = version;
            Loggers.DATA_STORAGE.debug("Data storage initialized");
        } finally {
            rwLock.writeLock().unlock();
        }

    }

    @Override
    public DataStoreTypes.Item get(DataStoreTypes.Category category, String key) {
        rwLock.readLock().lock();
        try {
            Map<String, DataStoreTypes.Item> items = allData.get(category);
            if (items == null) return null;
            DataStoreTypes.Item item = items.get(key);
            if (item == null || item.item().isArchived()) return null;
            return item;
        } finally {
            rwLock.readLock().unlock();
        }

    }

    @Override
    public Map<String, DataStoreTypes.Item> getAll(DataStoreTypes.Category category) {
        rwLock.readLock().lock();
        try {
            Map<String, DataStoreTypes.Item> items = allData.get(category);
            if (items == null) return null;
            Map<String, DataStoreTypes.Item> map = items.entrySet().stream().filter(entry -> !entry.getValue().item().isArchived()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            return ImmutableMap.copyOf(map);
        } finally {
            rwLock.readLock().unlock();
        }

    }


    @Override
    public boolean upsert(DataStoreTypes.Category category, String key, DataStoreTypes.Item item, Long version) {
        if (version == null || this.version >= version || item == null || item.item() == null) {
            return false;
        }
        rwLock.writeLock().lock();
        try {
            Map<String, DataStoreTypes.Item> oldItems = allData.get(category);
            DataStoreTypes.Item oldItem = null;
            if (oldItems != null) {
                oldItem = oldItems.get(key);
                if (oldItem != null && oldItem.item().getTimestamp() >= item.item().getTimestamp()) return false;
            }
            // the data cannot change in any way once an instance of the Immutable Map is created.
            // we should re-initialize a new internal map when update
            ImmutableMap.Builder<DataStoreTypes.Category, Map<String, DataStoreTypes.Item>> newData = ImmutableMap.builder();
            for (Map.Entry<DataStoreTypes.Category, Map<String, DataStoreTypes.Item>> entry : allData.entrySet()) {
                if (!entry.getKey().equals(category)) {
                    newData.put(entry.getKey(), entry.getValue());
                }
            }
            if (oldItems == null) {
                newData.put(category, ImmutableMap.of(key, item));
            } else {
                ImmutableMap.Builder<String, DataStoreTypes.Item> newItems = ImmutableMap.builder();
                if (oldItem == null) {
                    newItems.putAll(oldItems);
                } else {
                    for (Map.Entry<String, DataStoreTypes.Item> entry : oldItems.entrySet()) {
                        if (!entry.getKey().equals(key)) {
                            newItems.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
                newItems.put(key, item);
                newData.put(category, newItems.build());
            }
            allData = newData.build();
            this.version = version;
            if (!initialized) initialized = true;
            Loggers.DATA_STORAGE.debug("upsert item {} into storage", key);
            return true;
        } finally {
            rwLock.writeLock().unlock();
        }

    }

    @Override
    public boolean isInitialized() {
        rwLock.readLock().lock();
        try {
            return initialized;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public long getVersion() {
        rwLock.readLock().lock();
        try {
            return version;
        } finally {
            rwLock.readLock().unlock();
        }
    }

    @Override
    public void close() {
    }
}
