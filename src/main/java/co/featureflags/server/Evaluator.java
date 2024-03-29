package co.featureflags.server;

import co.featureflags.commons.model.FFCUser;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

/**
 * Evaluation process is totally isolated from update process and data storage
 */

abstract class Evaluator {

    protected static final Logger logger = Loggers.EVALUATION;
    protected static final Integer NO_EVAL_RES = -1;

    protected static final String DEFAULT_JSON_VALUE = "DJV";

    protected static final String REASON_USER_NOT_SPECIFIED = "user not specified";
    protected static final String REASON_FLAG_OFF = "flag off";
    protected static final String REASON_PREREQUISITE_FAILED = "prerequisite failed";
    protected static final String REASON_TARGET_MATCH = "target match";
    protected static final String REASON_RULE_MATCH = "rule match";
    protected static final String REASON_FALLTHROUGH = "fall through all rules";
    protected static final String REASON_CLIENT_NOT_READY = "client not ready";
    protected static final String REASON_FLAG_NOT_FOUND = "flag not found";
    protected static final String REASON_WRONG_TYPE = "wrong type";
    protected static final String REASON_ERROR = "error in evaluation";
    protected static final String FLAG_KEY_UNKNOWN = "flag key unknown";
    protected static final String FLAG_NAME_UNKNOWN = "flag name unknown";

    protected static final String FLAG_VALUE_UNKNOWN = "flag value unknown";

    protected static final String THAN_CLAUSE = "Than";
    protected static final String GE_CLAUSE = "BiggerEqualThan";
    protected static final String GT_CLAUSE = "BiggerThan";
    protected static final String LE_CLAUSE = "LessEqualThan";
    protected static final String LT_CLAUSE = "LessThan";
    protected static final String EQ_CLAUSE = "Equal";
    protected static final String NEQ_CLAUSE = "NotEqual";
    protected static final String CONTAINS_CLAUSE = "Contains";
    protected static final String NOT_CONTAIN_CLAUSE = "NotContain";
    protected static final String IS_ONE_OF_CLAUSE = "IsOneOf";
    protected static final String NOT_ONE_OF_CLAUSE = "NotOneOf";
    protected static final String STARTS_WITH_CLAUSE = "StartsWith";
    protected static final String ENDS_WITH_CLAUSE = "EndsWith";
    protected static final String IS_TRUE_CLAUSE = "IsTrue";
    protected static final String IS_FALSE_CLAUSE = "IsFalse";
    protected static final String MATCH_REGEX_CLAUSE = "MatchRegex";
    protected static final String NOT_MATCH_REGEX_CLAUSE = "NotMatchRegex";
    protected static final String IS_IN_SEGMENT_CLAUSE = "User is in segment";
    protected static final String NOT_IN_SEGMENT_CLAUSE = "User is not in segment";


    protected static final String FLAG_DISABLE_STATS = "Disabled";
    protected static final String FLAG_ENABLE_STATS = "Enabled";

    protected final Getter<DataModel.FeatureFlag> flagGetter;

    protected final Getter<DataModel.Segment> segmentGetter;

    Evaluator(Getter<DataModel.FeatureFlag> flagGetter,
              Getter<DataModel.Segment> segmentGetter) {
        this.flagGetter = flagGetter;
        this.segmentGetter = segmentGetter;
    }

    abstract EvalResult evaluate(DataModel.FeatureFlag flag, FFCUser user, InsightTypes.Event event);

    @FunctionalInterface
    interface Getter<T extends DataModel.TimestampData> {
        T get(String key);
    }

    static class EvalResult {
        private final Integer index;
        private final String value;
        private final String reason;
        private final boolean sendToExperiment;
        private final String keyName;
        private final String name;


        EvalResult(String value, Integer index, String reason, boolean sendToExperiment, String keyName, String name) {
            this.value = value;
            this.index = index;
            this.reason = reason;
            this.sendToExperiment = sendToExperiment;
            this.keyName = keyName;
            this.name = name;
        }

        public static EvalResult error(String reason, String keyName, String name) {
            return new EvalResult(null, NO_EVAL_RES, reason, false, keyName, name);
        }

        public static EvalResult error(String defaultValue, String reason, String keyName, String name) {
            return new EvalResult(defaultValue, NO_EVAL_RES, reason, false, keyName, name);
        }

        public static EvalResult of(DataModel.VariationOption option,
                                    String reason,
                                    boolean sendToExperiment,
                                    String keyName,
                                    String name) {
            return new EvalResult(option.getVariationValue(),
                    option.getLocalId(),
                    reason,
                    sendToExperiment,
                    keyName,
                    name);
        }

        public String getValue() {
            return value;
        }

        public Integer getIndex() {
            return index;
        }

        public String getReason() {
            return reason;
        }

        public boolean isSendToExperiment() {
            return sendToExperiment;
        }

        public String getKeyName() {
            return keyName;
        }

        public String getName() {
            return name;
        }

        public boolean checkType(Object defaultValue) {
            if (value == null) {
                return false;
            }
            if (defaultValue instanceof String) {
                return true;
            }
            if (defaultValue instanceof Boolean && BooleanUtils.toBooleanObject(value) != null) {
                return true;
            }
            return (defaultValue instanceof Integer || defaultValue instanceof Long || defaultValue instanceof Double) && StringUtils.isNumeric(value);
        }
    }

}
