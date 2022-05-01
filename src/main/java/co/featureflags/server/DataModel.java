package co.featureflags.server;

import co.featureflags.commons.json.JsonHelper;
import co.featureflags.commons.model.UserTag;
import co.featureflags.server.exterior.DataStoreTypes;
import com.google.common.collect.ImmutableMap;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

public abstract class DataModel {

    private DataModel() {
    }

    /**
     * interface for the object to represent a versioned/timestamped data
     */
    public interface TimestampData {
        Integer FFC_FEATURE_FLAG = 100;
        Integer FFC_ARCHIVED_VDATA = 200;
        Integer FFC_PERSISTENT_VDATA = 300;
        Integer FFC_SEGMENT = 400;
        Integer FFC_USER_TAG = 500;

        /**
         * return the unique id
         *
         * @return a string
         */
        String getId();

        /**
         * return true if object is archived
         *
         * @return true if object is archived
         */
        boolean isArchived();

        /**
         * return the version/timestamp of the object
         *
         * @return a long value
         */
        Long getTimestamp();

        /**
         * return the type of versioned/timestamped object
         *
         * @return an integer
         */
        Integer getType();
    }

    /**
     * the object is an implementation of{@link TimestampData}, to represent the archived data
     */
    public final static class ArchivedTimestampData implements TimestampData {
        private final String id;
        private final Long timestamp;
        private final Boolean isArchived = Boolean.TRUE;

        public ArchivedTimestampData(String id, Long timestamp) {
            this.id = id;
            this.timestamp = timestamp;
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
            return FFC_ARCHIVED_VDATA;
        }
    }

    static class StreamingMessage {
        static final String DATA_SYNC = "data-sync";
        static final String PING = "ping";
        static final String PONG = "pong";

        protected final String messageType;

        StreamingMessage(String messageType) {
            this.messageType = messageType;
        }

        public String getMessageType() {
            return messageType;
        }
    }

    static class DataSyncMessage extends StreamingMessage {
        final InternalData data;

        DataSyncMessage(Long timestamp) {
            super(timestamp == null ? PING : DATA_SYNC);
            this.data = timestamp == null ? null : new InternalData(timestamp);
        }

        static class InternalData {
            Long timestamp;

            InternalData(Long timestamp) {
                this.timestamp = timestamp;
            }
        }
    }

    static class All extends StreamingMessage {
        private final Data data;

        All(String messageType, Data data) {
            super(messageType);
            this.data = data;
        }

        public Data data() {
            return data;
        }

        boolean isProcessData() {
            return DATA_SYNC.equalsIgnoreCase(messageType) && data != null && ("full".equalsIgnoreCase(data.eventType) || "patch".equalsIgnoreCase(data.eventType));
        }
    }

    /**
     * versioned data of feature flags and related data from feature-flag.co
     */
    @JsonAdapter(JsonHelper.AfterJsonParseDeserializableTypeAdapterFactory.class)
    static class Data implements JsonHelper.AfterJsonParseDeserializable {

        private final String eventType;
        private final List<FeatureFlag> featureFlags;
        private final List<Segment> segments;
        private final List<TimestampUserTag> userTags;
        private Long timestamp;

        Data(String eventType, List<FeatureFlag> featureFlags, List<Segment> segments, List<TimestampUserTag> userTags) {
            this.eventType = eventType;
            this.featureFlags = featureFlags;
            this.segments = segments;
            this.userTags = userTags;
        }

        @Override
        public void afterDeserialization() {
            Long v1 = (featureFlags != null) ? featureFlags.stream().map(flag -> flag.timestamp).max(Long::compare).orElse(0L) : 0L;
            Long v2 = (segments != null) ? segments.stream().map(segment -> segment.timestamp).max(Long::compare).orElse(0L) : 0L;
            Long v3 = (userTags != null) ? userTags.stream().map(tag -> tag.timestamp).max(Long::compare).orElse(0L) : 0L;
            timestamp = Math.max(v1, v2);
            timestamp = Math.max(timestamp, v3);
        }

        public List<FeatureFlag> getFeatureFlags() {
            return featureFlags == null ? Collections.emptyList() : featureFlags;
        }

        public List<Segment> getSegments() {
            return segments == null ? Collections.emptyList() : segments;
        }

        public List<TimestampUserTag> getUserTags() {
            return userTags == null ? Collections.emptyList() : userTags;
        }

        public String getEventType() {
            return eventType;
        }

        public Long getTimestamp() {
            return timestamp;
        }

        Map<DataStoreTypes.Category, Map<String, DataStoreTypes.Item>> toStorageType() {
            ImmutableMap.Builder<String, DataStoreTypes.Item> flags = ImmutableMap.builder();
            for (FeatureFlag flag : getFeatureFlags()) {
                TimestampData data = flag.isArchived ? flag.toArchivedTimestampData() : flag;
                flags.put(data.getId(), new DataStoreTypes.Item(data));
            }
            ImmutableMap.Builder<String, DataStoreTypes.Item> segments = ImmutableMap.builder();
            for (Segment segment : getSegments()) {
                TimestampData data = segment.isArchived ? segment.toArchivedTimestampData() : segment;
                segments.put(data.getId(), new DataStoreTypes.Item(data));
            }
            ImmutableMap.Builder<String, DataStoreTypes.Item> userTags = ImmutableMap.builder();
            for (TimestampUserTag userTag : getUserTags()) {
                TimestampData data = userTag.isArchived ? userTag.toArchivedTimestampData() : userTag;
                userTags.put(data.getId(), new DataStoreTypes.Item(data));
            }
            return ImmutableMap.of(DataStoreTypes.FEATURES, flags.build(), DataStoreTypes.SEGMENTS, segments.build(), DataStoreTypes.USERTAGS, userTags.build());
        }
    }

    static class TimestampUserTag extends UserTag implements TimestampData {

        private final String id;

        private final Boolean isArchived;

        private final Long timestamp;

        TimestampUserTag(String id, Boolean isArchived, Long timestamp, String requestProperty, String source, String userProperty) {
            super(requestProperty, source, userProperty);
            this.id = id;
            this.isArchived = isArchived;
            this.timestamp = timestamp;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public boolean isArchived() {
            return isArchived != null && isArchived;
        }

        @Override
        public Long getTimestamp() {
            return timestamp;
        }

        @Override
        public Integer getType() {
            return FFC_USER_TAG;
        }

        public TimestampData toArchivedTimestampData() {
            return new ArchivedTimestampData(this.id, this.timestamp);
        }
    }

    static class Segment implements TimestampData {

        private final String id;

        private final Boolean isArchived;

        private final Long timestamp;

        private final List<String> included;

        private final List<String> excluded;

        private final List<TargetRule> rules;

        Segment(String id,
                Boolean isArchived,
                Long timestamp,
                List<String> included,
                List<String> excluded,
                List<TargetRule> rules) {
            this.id = id;
            this.isArchived = isArchived;
            this.timestamp = timestamp;
            this.included = included;
            this.excluded = excluded;
            this.rules = rules;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public boolean isArchived() {
            return isArchived != null && isArchived;
        }

        @Override
        public Long getTimestamp() {
            return timestamp;
        }

        @Override
        public Integer getType() {
            return FFC_SEGMENT;
        }

        public List<String> getIncluded() {
            return included == null ? Collections.emptyList() : included;
        }

        public List<String> getExcluded() {
            return excluded == null ? Collections.emptyList() : excluded;
        }

        public List<TargetRule> getRules() {
            return rules == null ? Collections.emptyList() : rules;
        }

        public Boolean isMatchUser(String userKeyId) {
            if (getExcluded().contains(userKeyId)) {
                return Boolean.FALSE;
            }

            if (getIncluded().contains(userKeyId)) {
                return Boolean.TRUE;
            }
            // if no included or excluded, then it's to match rules
            return null;
        }

        public TimestampData toArchivedTimestampData() {
            return new ArchivedTimestampData(this.id, this.timestamp);
        }
    }

    static class FeatureFlag implements TimestampData {
        private final String id;
        private final Boolean isArchived;
        private final Long timestamp;
        private final Boolean exptIncludeAllRules;
        @SerializedName("ff")
        private final FeatureFlagBasicInfo info;
        @SerializedName("ffp")
        private final List<FeatureFlagPrerequisite> prerequisites;
        @SerializedName("fftuwmtr")
        private final List<TargetRule> rules;
        @SerializedName("targetIndividuals")
        private final List<TargetIndividuals> targets;
        @SerializedName("variationOptions")
        private final List<VariationOption> variations;

        FeatureFlag(String id, Boolean isArchived, Long timestamp, Boolean exptIncludeAllRules, FeatureFlagBasicInfo info, List<FeatureFlagPrerequisite> prerequisites, List<TargetRule> rules, List<TargetIndividuals> targets, List<VariationOption> variations) {
            this.id = id;
            this.isArchived = isArchived;
            this.timestamp = timestamp;
            this.exptIncludeAllRules = exptIncludeAllRules;
            this.info = info;
            this.prerequisites = prerequisites;
            this.rules = rules;
            this.targets = targets;
            this.variations = variations;
        }

        public TimestampData toArchivedTimestampData() {
            return new ArchivedTimestampData(this.id, this.timestamp);
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public boolean isArchived() {
            return isArchived != null && isArchived;
        }

        @Override
        public Long getTimestamp() {
            return timestamp;
        }

        @Override
        public Integer getType() {
            return FFC_FEATURE_FLAG;
        }

        public Boolean isExptIncludeAllRules() {
            return exptIncludeAllRules;
        }

        public FeatureFlagBasicInfo getInfo() {
            return info;
        }

        public List<FeatureFlagPrerequisite> getPrerequisites() {
            return prerequisites == null ? Collections.emptyList() : prerequisites;
        }

        public List<TargetRule> getRules() {
            return rules == null ? Collections.emptyList() : rules;
        }

        public List<TargetIndividuals> getTargets() {
            return targets == null ? Collections.emptyList() : targets;
        }

        public List<VariationOption> getVariations() {
            return variations == null ? Collections.emptyList() : variations;
        }
    }

    static class FeatureFlagBasicInfo {
        private final String id;
        private final String name;
        private final Integer type;
        private final String keyName;
        private final String status;
        private final Boolean isDefaultRulePercentageRolloutsIncludedInExpt;
        private final Date lastUpdatedTime;
        private final List<VariationOptionPercentageRollout> defaultRulePercentageRollouts;
        private final VariationOption variationOptionWhenDisabled;

        FeatureFlagBasicInfo(String id, String name, Integer type, String keyName, String status, Boolean isDefaultRulePercentageRolloutsIncludedInExpt, Date lastUpdatedTime, List<VariationOptionPercentageRollout> defaultRulePercentageRollouts, VariationOption variationOptionWhenDisabled) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.keyName = keyName;
            this.status = status;
            this.isDefaultRulePercentageRolloutsIncludedInExpt = isDefaultRulePercentageRolloutsIncludedInExpt;
            this.lastUpdatedTime = lastUpdatedTime;
            this.defaultRulePercentageRollouts = defaultRulePercentageRollouts;
            this.variationOptionWhenDisabled = variationOptionWhenDisabled;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public Integer getType() {
            return type;
        }

        public String getKeyName() {
            return keyName;
        }

        public String getStatus() {
            return status;
        }

        public Boolean isDefaultRulePercentageRolloutsIncludedInExpt() {
            return isDefaultRulePercentageRolloutsIncludedInExpt;
        }

        public Date getLastUpdatedTime() {
            return lastUpdatedTime;
        }

        public List<VariationOptionPercentageRollout> getDefaultRulePercentageRollouts() {
            return defaultRulePercentageRollouts == null ? Collections.emptyList() : defaultRulePercentageRollouts;
        }

        public VariationOption getVariationOptionWhenDisabled() {
            return variationOptionWhenDisabled;
        }
    }

    static class FeatureFlagPrerequisite {
        private final String prerequisiteFeatureFlagId;
        private final VariationOption ValueOptionsVariationValue;

        FeatureFlagPrerequisite(String prerequisiteFeatureFlagId, VariationOption valueOptionsVariationValue) {
            this.prerequisiteFeatureFlagId = prerequisiteFeatureFlagId;
            ValueOptionsVariationValue = valueOptionsVariationValue;
        }

        public String getPrerequisiteFeatureFlagId() {
            return prerequisiteFeatureFlagId;
        }

        public VariationOption getValueOptionsVariationValue() {
            return ValueOptionsVariationValue;
        }
    }

    static class TargetRule {
        private final String ruleId;
        private final String ruleName;
        private final Boolean isIncludedInExpt;
        private final List<RuleItem> ruleJsonContent;
        private final List<VariationOptionPercentageRollout> valueOptionsVariationRuleValues;

        TargetRule(String ruleId, String ruleName, Boolean isIncludedInExpt, List<RuleItem> ruleJsonContent, List<VariationOptionPercentageRollout> valueOptionsVariationRuleValues) {
            this.ruleId = ruleId;
            this.ruleName = ruleName;
            this.isIncludedInExpt = isIncludedInExpt;
            this.ruleJsonContent = ruleJsonContent;
            this.valueOptionsVariationRuleValues = valueOptionsVariationRuleValues;
        }

        public String getRuleId() {
            return ruleId;
        }

        public String getRuleName() {
            return ruleName;
        }

        public Boolean isIncludedInExpt() {
            return isIncludedInExpt;
        }

        public List<RuleItem> getRuleJsonContent() {
            return ruleJsonContent == null ? Collections.emptyList() : ruleJsonContent;
        }

        public List<VariationOptionPercentageRollout> getValueOptionsVariationRuleValues() {
            return valueOptionsVariationRuleValues == null ? Collections.emptyList() : valueOptionsVariationRuleValues;
        }
    }

    static class TargetIndividuals {
        private final List<FeatureFlagTargetIndividualUser> individuals;
        private final VariationOption valueOption;

        TargetIndividuals(List<FeatureFlagTargetIndividualUser> individuals, VariationOption valueOption) {
            this.individuals = individuals;
            this.valueOption = valueOption;
        }

        public List<FeatureFlagTargetIndividualUser> getIndividuals() {
            return individuals == null ? Collections.emptyList() : individuals;
        }

        public VariationOption getValueOption() {
            return valueOption;
        }

        public boolean isTargeted(String userKeyId) {
            if (individuals == null) return false;
            return individuals.stream().anyMatch(i -> i.keyId.equals(userKeyId));
        }
    }

    static class VariationOption {
        private final Integer localId;
        private final Integer displayOrder;
        private final String variationValue;

        VariationOption(Integer localId, Integer displayOrder, String variationValue) {
            this.localId = localId;
            this.displayOrder = displayOrder;
            this.variationValue = variationValue;
        }

        public Integer getLocalId() {
            return localId;
        }

        public Integer getDisplayOrder() {
            return displayOrder;
        }

        public String getVariationValue() {
            return variationValue;
        }

    }

    static class VariationOptionPercentageRollout {
        private final Double exptRollout;
        private final List<Double> rolloutPercentage;
        private final VariationOption valueOption;

        VariationOptionPercentageRollout(Double exptRollout, List<Double> rolloutPercentage, VariationOption valueOption) {
            this.exptRollout = exptRollout;
            this.rolloutPercentage = rolloutPercentage;
            this.valueOption = valueOption;
        }

        public Double getExptRollout() {
            return exptRollout;
        }

        public List<Double> getRolloutPercentage() {
            return rolloutPercentage == null ? Arrays.asList(0D, 1D) : rolloutPercentage;
        }

        public VariationOption getValueOption() {
            return valueOption;
        }
    }

    static class RuleItem {
        private final String property;
        private final String operation;
        private final String value;

        RuleItem(String property, String operation, String value) {
            this.property = property;
            this.operation = operation;
            this.value = value;
        }

        public String getProperty() {
            return property == null ? "" : property;
        }

        public String getOperation() {
            return operation == null ? "" : operation;
        }

        public String getValue() {
            return value == null ? "" : value;
        }
    }

    static class FeatureFlagTargetIndividualUser {
        private final String id;
        private final String name;
        private final String keyId;
        private final String email;

        FeatureFlagTargetIndividualUser(String id, String name, String keyId, String email) {
            this.id = id;
            this.name = name;
            this.keyId = keyId;
            this.email = email;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getKeyId() {
            return keyId;
        }

        public String getEmail() {
            return email;
        }

    }

}
