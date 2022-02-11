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

        public Category(String name, String pollingApiUrl, String streamingApiUrl) {
            this.name = name;
            this.pollingApiUrl = pollingApiUrl;
            this.streamingApiUrl = streamingApiUrl;
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

    public static final class PersistentItem implements Serializable {
        private final String id;
        private final Integer version;
        private final boolean isArchived;
        private final String json;

        public PersistentItem(String id,
                              Integer version,
                              boolean isArchived,
                              String json) {
            this.id = id;
            this.version = version;
            this.isArchived = isArchived;
            this.json = json;
        }

        public String getId() {
            return id;
        }

        public Integer getVersion() {
            return version;
        }

        public boolean isArchived() {
            return isArchived;
        }

        public String getJson() {
            return json;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PersistentItem that = (PersistentItem) o;
            return isArchived == that.isArchived && Objects.equals(id, that.id) && Objects.equals(version, that.version) && Objects.equals(json, that.json);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, version, isArchived, json);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("id", id)
                    .add("version", version)
                    .add("isArchived", isArchived)
                    .add("json", json)
                    .toString();
        }
    }


}
