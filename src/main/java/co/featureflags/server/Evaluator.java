package co.featureflags.server;

import co.featureflags.server.exterior.model.FFCUser;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

/**
 * Evaluation process is totally isolated from update process and data storage
 */

abstract class Evaluator {

    protected static final Logger logger = Loggers.EVALUATION;
    protected static final Integer NO_EVAL_RES = -1;

    protected static final String REASON_USER_NOT_SPECIFIED = "user not specified";
    protected static final String REASON_FLAG_OFF = "flag off";
    protected static final String REASON_PREREQUISITE_FAILED = "prerequisite failed";
    protected static final String REASON_TARGET_MATCH = "target match";
    protected static final String REASON_RULE_MATCH = "rule match";
    protected static final String REASON_FALLTHROUGH = "fall through all rules";

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


    protected static final String FLAG_DISABLE_STATS = "Disabled";
    protected static final String FLAG_ENABLE_STATS = "Enabled";

    protected final Getter<DataModel.FeatureFlag> flagGetter;

    Evaluator(Getter<DataModel.FeatureFlag> flagGetter) {
        this.flagGetter = flagGetter;
    }

    abstract EvalResult evaluate(DataModel.FeatureFlag flag,
                                 FFCUser user,
                                 FeatureFlagKeyExtension.FeatureFlagIdByEnvSecret flagId);

    static enum EvalType {
        Null(0),
        String(1),
        Boolean(2),
        Number(3),
        Object(4);

        private int code;

        EvalType(int code) {
            this.code = code;
        }

        public static EvalType checkType(String value) {
            if (value == null)
                return Null;
            if (StringUtils.isNumeric(value)) {
                return Number;
            }
            if (BooleanUtils.toBooleanObject(value) != null) {
                return Boolean;
            }
            return String;
        }

        public int getCode() {
            return code;
        }
    }

    @FunctionalInterface
    interface Getter<T extends DataModel.TimestampData> {
        T get(String key);
    }

    static class EvalResult {
        private final Integer index;
        private final String value;
        private final String reason;
        private final EvalType type;
        ;


        EvalResult(
                String value,
                Integer index,
                String reason) {
            this.value = value;
            this.index = index;
            this.reason = reason;
            this.type = EvalType.checkType(value);
        }

        public static EvalResult error(String reason) {
            return new EvalResult(null, NO_EVAL_RES, reason);
        }

        public static EvalResult of(DataModel.VariationOption option, String reason) {
            return new EvalResult(option.getVariationValue(), option.getLocalId(), reason);
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

        public EvalType getType() {
            return type;
        }
    }

}
