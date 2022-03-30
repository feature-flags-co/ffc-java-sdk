package co.featureflags.server.exterior;

import co.featureflags.server.DataModel;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

import java.io.Serializable;
import java.util.*;

/**
 * Contains information about the internal data model for storage objects
 * <p>
 * The implementation of internal components is not public to application code (although of course developers can easily
 * look at the code or the data) so that changes to SDK implementation details will not be breaking changes to the application.
 * This class provide a high-level description of storage objects so that custom integration code or test code can
 * store or serialize them.
 */
public abstract class DataStoreTypes {

    /**
     * The {@link Category} instance that describes feature flag data.
     * <p>
     * Applications should not need to reference this object directly.It is public so that custom integrations
     * and test code can serialize or deserialize data or inject it into a data storage.
     */
    public final static Category FEATURES = new Category("featureFlags",
            "/api/public/sdk/latest-feature-flags",
            "/streaming");

    public final static Category SEGMENTS = new Category("segments",
            "/api/public/sdk/latest-feature-flags",
            "/streaming");

    public final static Category USERTAGS = new Category("userTags",
            "/api/public/sdk/latest-feature-flags",
            "/streaming");

    /**
     * An enumeration of all supported {@link Category} types.
     * <p>
     * Applications should not need to reference this object directly. It is public so that custom data storage
     * implementations can determine what kinds of model objects may need to be stored.
     */

    public final List<Category> FFC_ALL_CATS = ImmutableList.of(FEATURES);

    private DataStoreTypes() {
    }

    /**
     * Represents a separated namespace of storable data items.
     * <p>
     * The SDK passes instances of this type to the data store to specify whether it is referring to
     * a feature flag, a user segment, etc
     */
    public static final class Category implements Serializable {
        private final String name;
        private final String pollingApiUrl;
        private final String streamingApiUrl;

        private Category(String name, String pollingApiUrl, String streamingApiUrl) {
            this.name = name;
            this.pollingApiUrl = pollingApiUrl;
            this.streamingApiUrl = streamingApiUrl;
        }

        /**
         * build a external category
         *
         * @param name the name of namespace
         * @return a Category
         */
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

    /**
     * Object, that contains s versioned item (or placeholder), storable in a {@link DataStorage}.
     * <p>
     * This is used for the default memory data storage that directly store objects in memory.
     */

    public static final class Item {
        private final DataModel.TimestampData item;

        public Item(DataModel.TimestampData data) {
            this.item = data;
        }

        /**
         * Returns a version object or an archived object if the object is archived
         *
         * @return a {@link co.featureflags.server.DataModel.TimestampData}
         */
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

    /**
     * Object, that contains s versioned item (or placeholder), storable in a {@link DataStorage}.
     * <p>
     * This is equivalent to {@link Item}, but is used for persistent data storage(like redis, mongodb etc.). The
     * SDK will convert each data item to and from its json string form; the persistent data
     * store deals only with the json form.
     */
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

        /**
         * build a PersistentItem instance
         *
         * @param id         unique id of PersistentItem
         * @param timestamp  the version number, Ordinarily it's a timestamped value
         * @param isArchived true if it's an archived object
         * @param json       the json string
         * @return a PersistentItem instance
         */
        public static PersistentItem of(String id,
                                        Long timestamp,
                                        Boolean isArchived,
                                        String json) {
            return new PersistentItem(id, timestamp, isArchived, json);
        }

        /**
         * return unique id of PersistentItem
         *
         * @return a string
         */
        @Override
        public String getId() {
            return id;
        }

        /**
         * return true if it's an archived object
         *
         * @return true if it's an archived object
         */
        @Override
        public boolean isArchived() {
            return isArchived;
        }

        /**
         * return the version number, Ordinarily it's a timestamped value
         *
         * @return a long value
         */
        @Override
        public Long getTimestamp() {
            return timestamp;
        }

        /**
         * return the type of PersistentItem
         *
         * @return a int
         * @see co.featureflags.server.DataModel.TimestampData
         */
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
