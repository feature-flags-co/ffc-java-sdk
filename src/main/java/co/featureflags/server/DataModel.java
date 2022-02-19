package co.featureflags.server;

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

    public interface TimestampData {
        Integer FFC_FEATURE_FLAG = 100;
        Integer FFC_ARCHIVED_VDATA = 200;
        Integer FFC_PERSISTENT_VDATA = 300;

        String getId();

        boolean isArchived();

        Long getTimestamp();

        Integer getType();
    }

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

    static class DataSyncMessage {
        final String messageType = "data-sync";
        final InternalData data;

        DataSyncMessage(Long timestamp) {
            this.data = new InternalData(timestamp);
        }

        private static class InternalData {
            Long timestamp;

            private InternalData(Long timestamp) {
                this.timestamp = timestamp;
            }
        }
    }

    static class All {
        private final String messageType;
        private final Data data;

        All(String messageType, Data data) {
            this.messageType = messageType;
            this.data = data;
        }

        public Data data() {
            return data;
        }

        public String getMessageType() {
            return messageType;
        }

        boolean isProcessData() {
            return "data-sync".equalsIgnoreCase(messageType) && data != null
                    && ("full".equalsIgnoreCase(data.eventType) || "patch".equalsIgnoreCase(data.eventType));
        }
    }

    /**
     * versioned data of feature flags and related data from feature-flag.co
     */
    @JsonAdapter(JsonHelper.AfterJsonParseDeserializableTypeAdapterFactory.class)
    static class Data implements JsonHelper.AfterJsonParseDeserializable {

        private final String eventType;
        private final List<FeatureFlag> featureFlags;
        private Long timestamp;

        Data(String eventType, List<FeatureFlag> featureFlags) {
            this.eventType = eventType;
            this.featureFlags = featureFlags;
        }

        @Override
        public void afterDeserialization() {
            timestamp = (featureFlags != null)
                    ? featureFlags.stream().map(flag -> flag.timestamp).max(Long::compare).orElse(0L) : 0L;
        }

        public List<FeatureFlag> getFeatureFlags() {
            return featureFlags == null ? Collections.emptyList() : featureFlags;
        }

        public String getEventType() {
            return eventType;
        }

        public Long getTimestamp() {
            return timestamp;
        }

        Map<DataStoreTypes.Category, Map<String, DataStoreTypes.Item>> toStorageType() {
            ImmutableMap.Builder<String, DataStoreTypes.Item> newItems = ImmutableMap.builder();
            for (FeatureFlag flag : getFeatureFlags()) {
                TimestampData data = flag.isArchived ? flag.toArchivedTimestampData() : flag;
                newItems.put(data.getId(), new DataStoreTypes.Item(data));
            }
            return ImmutableMap.of(DataStoreTypes.FEATURES, newItems.build());
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
        private final List<FeatureFlagTargetUsersWhoMatchTheseRuleParam> rules;
        @SerializedName("targetIndividuals")
        private final List<TargetIndividualForVariationOption> targets;
        @SerializedName("variationOptions")
        private final List<VariationOption> variations;

        FeatureFlag(String id,
                    Boolean isArchived,
                    Long timestamp,
                    Boolean exptIncludeAllRules,
                    FeatureFlagBasicInfo info,
                    List<FeatureFlagPrerequisite> prerequisites,
                    List<FeatureFlagTargetUsersWhoMatchTheseRuleParam> rules,
                    List<TargetIndividualForVariationOption> targets,
                    List<VariationOption> variations) {
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
            return exptIncludeAllRules == null ? Boolean.FALSE : exptIncludeAllRules;
        }

        public FeatureFlagBasicInfo getInfo() {
            return info;
        }

        public List<FeatureFlagPrerequisite> getPrerequisites() {
            return prerequisites == null ? Collections.emptyList() : prerequisites;
        }

        public List<FeatureFlagTargetUsersWhoMatchTheseRuleParam> getRules() {
            return rules == null ? Collections.emptyList() : rules;
        }

        public List<TargetIndividualForVariationOption> getTargets() {
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

        FeatureFlagBasicInfo(String id,
                             String name,
                             Integer type,
                             String keyName,
                             String status,
                             Boolean isDefaultRulePercentageRolloutsIncludedInExpt,
                             Date lastUpdatedTime,
                             List<VariationOptionPercentageRollout> defaultRulePercentageRollouts,
                             VariationOption variationOptionWhenDisabled) {
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
            return isDefaultRulePercentageRolloutsIncludedInExpt == null
                    ? Boolean.FALSE : isDefaultRulePercentageRolloutsIncludedInExpt;
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

        FeatureFlagPrerequisite(String prerequisiteFeatureFlagId,
                                VariationOption valueOptionsVariationValue) {
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

    static class FeatureFlagTargetUsersWhoMatchTheseRuleParam {
        private final String ruleId;
        private final String ruleName;
        private final Boolean isIncludedInExpt;
        private final List<FeatureFlagRuleJsonContent> ruleJsonContent;
        private final List<VariationOptionPercentageRollout> valueOptionsVariationRuleValues;

        FeatureFlagTargetUsersWhoMatchTheseRuleParam(String ruleId,
                                                     String ruleName,
                                                     Boolean isIncludedInExpt,
                                                     List<FeatureFlagRuleJsonContent> ruleJsonContent,
                                                     List<VariationOptionPercentageRollout> valueOptionsVariationRuleValues) {
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
            return isIncludedInExpt == null ? Boolean.FALSE : isIncludedInExpt;
        }

        public List<FeatureFlagRuleJsonContent> getRuleJsonContent() {
            return ruleJsonContent == null ? Collections.emptyList() : ruleJsonContent;
        }

        public List<VariationOptionPercentageRollout> getValueOptionsVariationRuleValues() {
            return valueOptionsVariationRuleValues == null ? Collections.emptyList() : valueOptionsVariationRuleValues;
        }
    }

    static class TargetIndividualForVariationOption {
        private final List<FeatureFlagTargetIndividualUser> individuals;
        private final VariationOption valueOption;

        TargetIndividualForVariationOption(List<FeatureFlagTargetIndividualUser> individuals,
                                           VariationOption valueOption) {
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
            if (individuals == null)
                return false;
            return individuals.stream().anyMatch(i -> i.keyId.equals(userKeyId));
        }
    }

    static class VariationOption {
        private final Integer localId;
        private final Integer displayOrder;
        private final String variationValue;

        VariationOption(Integer localId,
                        Integer displayOrder,
                        String variationValue) {
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

        VariationOptionPercentageRollout(Double exptRollout,
                                         List<Double> rolloutPercentage,
                                         VariationOption valueOption) {
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

    static class FeatureFlagRuleJsonContent {
        private final String property;
        private final String operation;
        private final String value;

        FeatureFlagRuleJsonContent(String property,
                                   String operation,
                                   String value) {
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

        FeatureFlagTargetIndividualUser(String id,
                                        String name,
                                        String keyId,
                                        String email) {
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
