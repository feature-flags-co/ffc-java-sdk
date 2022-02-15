package co.featureflags.server.exterior;

import co.featureflags.server.DataModel;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

import java.io.Serializable;
import java.util.*;

public abstract class DataStoreTypes {

    public final static Category FEATURES = new Category("featureFlags",
            "/api/public/sdk/latest-feature-flags",
            "streaming");

    public final List<Category> FFC_ALL_CATS = ImmutableList.of(FEATURES);

    private DataStoreTypes() {
    }

    public static final class Category implements Serializable {
        private final String name;
        private final String pollingApiUrl;
        private final String streamingApiUrl;

        private Category(String name, String pollingApiUrl, String streamingApiUrl) {
            this.name = name;
            this.pollingApiUrl = pollingApiUrl;
            this.streamingApiUrl = streamingApiUrl;
        }

        public static Category of(String name) {
            return new Category(name, "unknown", "unknown");
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("name", name)
                    .add("pollingApiUrl", pollingApiUrl)
                    .add("streamingApiUrl", streamingApiUrl)
                    .toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Category category = (Category) o;
            return Objects.equals(name, category.name)
                    && Objects.equals(pollingApiUrl, category.pollingApiUrl)
                    && Objects.equals(streamingApiUrl, category.streamingApiUrl);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, pollingApiUrl, streamingApiUrl);
        }
    }

    public static final class Item {
        private final DataModel.TimestampData item;

        public Item(DataModel.TimestampData data) {
            this.item = data;
        }

        public DataModel.TimestampData item() {
            return item;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Item item1 = (Item) o;
            return Objects.equals(item, item1.item);
        }

        @Override
        public int hashCode() {
            return Objects.hash(item);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("item", item)
                    .toString();
        }
    }

    public static final class PersistentItem implements DataModel.TimestampData, Serializable {
        private final String id;
        private final Long timestamp;
        private final Boolean isArchived;
        private final String json;

        private PersistentItem(String id,
                               Long timestamp,
                               Boolean isArchived,
                               String json) {
            this.id = id;
            this.timestamp = timestamp;
            this.isArchived = isArchived;
            this.json = json;
        }

        public static PersistentItem of(String id,
                                        Long timestamp,
                                        Boolean isArchived,
                                        String json) {
            return new PersistentItem(id, timestamp, isArchived, json);
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public boolean isArchived() {
            return isArchived;
        }

        @Override
        public Long getTimestamp() {
            return timestamp;
        }

        @Override
        public Integer getType() {
            return FFC_PERSISTENT_VDATA;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PersistentItem that = (PersistentItem) o;
            return Objects.equals(id, that.id) && Objects.equals(timestamp, that.timestamp) && Objects.equals(isArchived, that.isArchived) && Objects.equals(json, that.json);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, timestamp, isArchived, json);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("id", id)
                    .add("timestamp", timestamp)
                    .add("isArchived", isArchived)
                    .add("json", json)
                    .toString();
        }
    }


}
