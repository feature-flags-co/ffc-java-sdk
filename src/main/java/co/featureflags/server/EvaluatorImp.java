package co.featureflags.server;

import co.featureflags.server.exterior.JsonParseException;
import co.featureflags.server.exterior.model.FFCUser;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class EvaluatorImp extends Evaluator {

    EvaluatorImp(Getter<DataModel.FeatureFlag> flagGetter) {
        super(flagGetter);
    }

    @Override
    EvalResult evaluate(DataModel.FeatureFlag flag, FFCUser user, FeatureFlagKeyExtension.FeatureFlagIdByEnvSecret flagId) {
        if (user == null) {
            return EvalResult.error(REASON_USER_NOT_SPECIFIED);
        }
        return matchUserVariation(flag, user, flagId);
    }

    private EvalResult matchUserVariation(DataModel.FeatureFlag flag, FFCUser user, FeatureFlagKeyExtension.FeatureFlagIdByEnvSecret flagId) {
        //return a value when flag is off or not match prerequisite rule
        EvalResult er = matchFeatureFlagDisabledUserVariation(flag, user, flagId);
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

    private EvalResult matchFeatureFlagDisabledUserVariation(DataModel.FeatureFlag flag, FFCUser user, FeatureFlagKeyExtension.FeatureFlagIdByEnvSecret flagId) {
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
                        // TODO
                        if (preFlag != null) {
                            EvalResult er = matchUserVariation(preFlag, user, flagId.copyForNewFlag(preFlagId));
                            // even if prerequisite flag is off, check if default value of prerequisite flag matches expected value
                            // if prerequisite failed, return the default value of this flag
                            if (!er.getIndex().equals(prerequisite.getValueOptionsVariationValue().getLocalId())) {
                                return true;
                            }
                        }
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
        String pv = user.getProperty(clause.getProperty().toLowerCase());
        //TODO add list of false keyword
        return pv != null && pv.equalsIgnoreCase("false");
    }

    private boolean matchRegExClause(FFCUser user, DataModel.FeatureFlagRuleJsonContent clause) {
        String pv = user.getProperty(clause.getProperty().toLowerCase());
        String clauseValue = clause.getValue();
        return pv != null && Pattern.compile(Pattern.quote(clauseValue), Pattern.CASE_INSENSITIVE)
                .matcher(pv)
                .find();
    }

    private boolean trueClause(FFCUser user, DataModel.FeatureFlagRuleJsonContent clause) {
        String pv = user.getProperty(clause.getProperty().toLowerCase());
        //TODO add list of true keyword
        return pv != null && pv.equalsIgnoreCase("true");
    }

    private boolean endsWithClause(FFCUser user, DataModel.FeatureFlagRuleJsonContent clause) {
        String pv = user.getProperty(clause.getProperty().toLowerCase());
        String clauseValue = clause.getValue();
        return pv != null && pv.endsWith(clauseValue);
    }

    private boolean startsWithClause(FFCUser user, DataModel.FeatureFlagRuleJsonContent clause) {
        String pv = user.getProperty(clause.getProperty().toLowerCase());
        String clauseValue = clause.getValue();
        return pv != null && pv.startsWith(clauseValue);
    }

    private boolean thanClause(FFCUser user, DataModel.FeatureFlagRuleJsonContent clause) {
        String pv = user.getProperty(clause.getProperty().toLowerCase());
        String clauseValue = clause.getValue();
        if (!StringUtils.isNumeric(pv) || !StringUtils.isNumeric(clauseValue)) {
            return false;
        }
        Double pvNumber = new BigDecimal(pv).setScale(5).doubleValue();
        Double cvNumber = new BigDecimal(clause.getValue()).setScale(5).doubleValue();
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
        String pv = user.getProperty(clause.getProperty().toLowerCase());
        String clauseValue = clause.getValue();
        return clauseValue.equals(pv);
    }

    private boolean containsClause(FFCUser user, DataModel.FeatureFlagRuleJsonContent clause) {
        String pv = user.getProperty(clause.getProperty().toLowerCase());
        String clauseValue = clause.getValue();
        return pv != null && pv.contains(clauseValue);
    }

    private boolean oneOfClause(FFCUser user, DataModel.FeatureFlagRuleJsonContent clause) {
        String pv = user.getProperty(clause.getProperty().toLowerCase());
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
