package co.featureflags.server;

import co.featureflags.server.exterior.JsonParseException;
import co.featureflags.server.exterior.model.FFCUser;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

final class EvaluatorImp extends Evaluator {

    EvaluatorImp(Getter<DataModel.FeatureFlag> flagGetter) {
        super(flagGetter);
    }

    @Override
    EvalResult evaluate(DataModel.FeatureFlag flag, FFCUser user) {
        if (user == null) {
            return EvalResult.error(REASON_USER_NOT_SPECIFIED);
        }
        return matchUserVariation(flag, user);
    }

    private EvalResult matchUserVariation(DataModel.FeatureFlag flag, FFCUser user) {
        //return a value when flag is off or not match prerequisite rule
        EvalResult er = matchFeatureFlagDisabledUserVariation(flag, user);
        if (er != null) {
            return er;
        }

        //return the value of target user
        er = matchTargetedUserVariation(flag, user);
        if (er != null) return er;

        //return the value of matched rule
        er = matchConditionedUserVariation(flag, user);
        if (er != null) {
            return er;
        }

        //get value from default rule
        er = matchDefaultUserVariation(flag, user);
        if (er != null) {
            return er;
        }
        // TODO useless code
        return EvalResult.of(flag.getInfo().getVariationOptionWhenDisabled(), REASON_FALLTHROUGH);
    }

    private EvalResult matchFeatureFlagDisabledUserVariation(DataModel.FeatureFlag flag, FFCUser user) {
        // case flag is off
        if (FLAG_DISABLE_STATS.equals(flag.getInfo().getStatus())) {
            return EvalResult.of(flag.getInfo().getVariationOptionWhenDisabled(), REASON_FLAG_OFF);
        }
        // case prerequisite is set
        return flag.getPrerequisites().stream()
                .filter(prerequisite -> {
                    String preFlagId = prerequisite.getPrerequisiteFeatureFlagId();
                    if (!preFlagId.equals(flag.getInfo().getId())) {
                        DataModel.FeatureFlag preFlag = this.flagGetter.get(preFlagId);
                        if (preFlag == null) {
                            String preFlagKey = FeatureFlagKeyExtension.unpackFeatureFlagId(preFlagId, 4);
                            logger.warn(String.format("prerequisite flag %s not found", preFlagKey));
                            return true;
                        }
                        EvalResult er = matchUserVariation(preFlag, user);
                        // even if prerequisite flag is off, check if default value of prerequisite flag matches expected value
                        // if prerequisite failed, return the default value of this flag
                        return !er.getIndex().equals(prerequisite.getValueOptionsVariationValue().getLocalId());

                    }
                    return false;
                }).findFirst()
                .map(prerequisite -> EvalResult.of(flag.getInfo().getVariationOptionWhenDisabled(), REASON_PREREQUISITE_FAILED))
                .orElse(null);
    }

    private EvalResult matchTargetedUserVariation(DataModel.FeatureFlag featureFlag, FFCUser user) {
        return featureFlag.getTargets().stream()
                .filter(target -> target.isTargeted(user.getKey()))
                .findFirst()
                .map(target -> EvalResult.of(target.getValueOption(), REASON_TARGET_MATCH))
                .orElse(null);
    }

    private EvalResult matchConditionedUserVariation(DataModel.FeatureFlag featureFlag, FFCUser user) {
        DataModel.FeatureFlagTargetUsersWhoMatchTheseRuleParam targetRule = featureFlag.getRules().stream()
                .filter(rule -> ifUserMatchRule(user, rule.getRuleJsonContent()))
                .findFirst()
                .orElse(null);
        // optional flatmap can't infer inner type of collection
        return targetRule == null ? null :
                getRollOutVariationOption(targetRule.getValueOptionsVariationRuleValues(), user, REASON_RULE_MATCH);


    }

    private boolean ifUserMatchRule(FFCUser user, List<DataModel.FeatureFlagRuleJsonContent> clauses) {
        return clauses.stream().allMatch(clause -> {
            boolean isInCondition = false;
            String op = clause.getOperation();
            if (op.contains(THAN_CLAUSE)) {
                isInCondition = thanClause(user, clause);
            } else if (op.equals(EQ_CLAUSE)) {
                isInCondition = equalsClause(user, clause);
            } else if (op.equals(NEQ_CLAUSE)) {
                isInCondition = !equalsClause(user, clause);
            } else if (op.equals(CONTAINS_CLAUSE)) {
                isInCondition = containsClause(user, clause);
            } else if (op.equals(NOT_CONTAIN_CLAUSE)) {
                isInCondition = !containsClause(user, clause);
            } else if (op.equals(IS_ONE_OF_CLAUSE)) {
                isInCondition = oneOfClause(user, clause);
            } else if (op.equals(NOT_ONE_OF_CLAUSE)) {
                isInCondition = !oneOfClause(user, clause);
            } else if (op.equals(STARTS_WITH_CLAUSE)) {
                isInCondition = startsWithClause(user, clause);
            } else if (op.equals(ENDS_WITH_CLAUSE)) {
                isInCondition = endsWithClause(user, clause);
            } else if (op.equals(IS_TRUE_CLAUSE)) {
                isInCondition = trueClause(user, clause);
            } else if (op.equals(IS_FALSE_CLAUSE)) {
                isInCondition = falseClause(user, clause);
            } else if (op.equals(MATCH_REGEX_CLAUSE)) {
                isInCondition = matchRegExClause(user, clause);
            } else if (op.equals(NOT_MATCH_REGEX_CLAUSE)) {
                isInCondition = !matchRegExClause(user, clause);
            }
            return isInCondition;
        });
    }

    private boolean falseClause(FFCUser user, DataModel.FeatureFlagRuleJsonContent clause) {
        String pv = user.getProperty(clause.getProperty());
        //TODO add list of false keyword
        return pv != null && pv.equalsIgnoreCase("false");
    }

    private boolean matchRegExClause(FFCUser user, DataModel.FeatureFlagRuleJsonContent clause) {
        String pv = user.getProperty(clause.getProperty());
        String clauseValue = clause.getValue();
        return pv != null && Pattern.compile(Pattern.quote(clauseValue), Pattern.CASE_INSENSITIVE)
                .matcher(pv)
                .find();
    }

    private boolean trueClause(FFCUser user, DataModel.FeatureFlagRuleJsonContent clause) {
        String pv = user.getProperty(clause.getProperty());
        //TODO add list of true keyword
        return pv != null && pv.equalsIgnoreCase("true");
    }

    private boolean endsWithClause(FFCUser user, DataModel.FeatureFlagRuleJsonContent clause) {
        String pv = user.getProperty(clause.getProperty());
        String clauseValue = clause.getValue();
        return pv != null && pv.endsWith(clauseValue);
    }

    private boolean startsWithClause(FFCUser user, DataModel.FeatureFlagRuleJsonContent clause) {
        String pv = user.getProperty(clause.getProperty());
        String clauseValue = clause.getValue();
        return pv != null && pv.startsWith(clauseValue);
    }

    private boolean thanClause(FFCUser user, DataModel.FeatureFlagRuleJsonContent clause) {
        String pv = user.getProperty(clause.getProperty());
        String clauseValue = clause.getValue();
        if (!StringUtils.isNumeric(pv) || !StringUtils.isNumeric(clauseValue)) {
            return false;
        }
        double pvNumber = new BigDecimal(pv).setScale(5, RoundingMode.HALF_UP).doubleValue();
        double cvNumber = new BigDecimal(clauseValue).setScale(5,RoundingMode.HALF_UP).doubleValue();
        switch (clause.getOperation()) {
            case GE_CLAUSE:
                return pvNumber >= cvNumber;
            case GT_CLAUSE:
                return pvNumber > cvNumber;
            case LE_CLAUSE:
                return pvNumber <= cvNumber;
            case LT_CLAUSE:
                return pvNumber < cvNumber;
            default:
                return false;
        }
    }

    private boolean equalsClause(FFCUser user, DataModel.FeatureFlagRuleJsonContent clause) {
        String pv = user.getProperty(clause.getProperty());
        String clauseValue = clause.getValue();
        return clauseValue.equals(pv);
    }

    private boolean containsClause(FFCUser user, DataModel.FeatureFlagRuleJsonContent clause) {
        String pv = user.getProperty(clause.getProperty());
        String clauseValue = clause.getValue();
        return pv != null && pv.contains(clauseValue);
    }

    private boolean oneOfClause(FFCUser user, DataModel.FeatureFlagRuleJsonContent clause) {
        String pv = user.getProperty(clause.getProperty());
        try {
            List<String> clauseValues = JsonHelper.deserialize(clause.getValue(), new TypeToken<List<String>>() {
            }.getType());
            return pv != null && clauseValues.contains(pv);
        } catch (JsonParseException e) {
            return false;
        }
    }

    private EvalResult matchDefaultUserVariation(DataModel.FeatureFlag featureFlag, FFCUser user) {
        return getRollOutVariationOption(featureFlag.getInfo().getDefaultRulePercentageRollouts(), user, REASON_FALLTHROUGH);
    }

    private EvalResult getRollOutVariationOption(Collection<DataModel.VariationOptionPercentageRollout> rollouts,
                                                 FFCUser user,
                                                 String reason) {
        return rollouts.stream()
                .filter(rollout -> VariationSplittingAlgorithm.ifKeyBelongsPercentage(user.getKey(), rollout.getRolloutPercentage()))
                .findFirst().map(rollout -> EvalResult.of(rollout.getValueOption(), reason))
                .orElse(null);
    }


}
