package co.featureflags.server;

import co.featureflags.commons.json.JsonHelper;
import co.featureflags.commons.model.FFCUser;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

final class EvaluatorImp extends Evaluator {

    public EvaluatorImp(Getter<DataModel.FeatureFlag> flagGetter, Getter<DataModel.Segment> segmentGetter) {
        super(flagGetter, segmentGetter);
    }

    @Override
    EvalResult evaluate(DataModel.FeatureFlag flag, FFCUser user, InsightTypes.Event event) {
        if (user == null || flag == null) {
            throw new IllegalArgumentException("null flag or empty user");
        }
        return matchUserVariation(flag, user, event);

    }

    private EvalResult matchUserVariation(DataModel.FeatureFlag flag, FFCUser user, InsightTypes.Event event) {
        //return a value when flag is off or not match prerequisite rule
        EvalResult er = null;
        try {
            er = matchFeatureFlagDisabledUserVariation(flag, user, event);
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
            er = EvalResult.of(flag.getInfo().getVariationOptionWhenDisabled(), REASON_FALLTHROUGH, false, flag.getInfo().getKeyName(), flag.getInfo().getName());
            return er;
        } finally {
            if (er != null) {
                logger.info("FFC JAVA SDK: User {}, Feature Flag {}, Flag Value {}", user.getKey(), flag.getInfo().getKeyName(), er.getValue());
                if (event != null) {
                    event.add(InsightTypes.FlagEventVariation.of(flag.getInfo().getKeyName(), er));
                }
            }
        }
    }

    private EvalResult matchFeatureFlagDisabledUserVariation(DataModel.FeatureFlag flag, FFCUser
            user, InsightTypes.Event event) {
        // case flag is off
        if (FLAG_DISABLE_STATS.equals(flag.getInfo().getStatus())) {
            return EvalResult.of(flag.getInfo().getVariationOptionWhenDisabled(), REASON_FLAG_OFF, false, flag.getInfo().getKeyName(), flag.getInfo().getName());
        }
        // case prerequisite is set
        return flag.getPrerequisites().stream().filter(prerequisite -> {
                    String preFlagId = prerequisite.getPrerequisiteFeatureFlagId();
                    if (!preFlagId.equals(flag.getInfo().getId())) {
                        DataModel.FeatureFlag preFlag = this.flagGetter.get(preFlagId);
                        if (preFlag == null) {
                            String preFlagKey = FeatureFlagKeyExtension.unpackFeatureFlagId(preFlagId, 4);
                            logger.warn("prerequisite flag {} not found", preFlagKey);
                            return true;
                        }
                        EvalResult er = matchUserVariation(preFlag, user, event);
                        // even if prerequisite flag is off, check if default value of prerequisite flag matches expected value
                        // if prerequisite failed, return the default value of this flag
                        return !er.getIndex().equals(prerequisite.getValueOptionsVariationValue().getLocalId());

                    }
                    return false;
                }).findFirst()
                .map(prerequisite -> EvalResult.of(flag.getInfo().getVariationOptionWhenDisabled(), REASON_PREREQUISITE_FAILED, false, flag.getInfo().getKeyName(), flag.getInfo().getName()))
                .orElse(null);
    }

    private EvalResult matchTargetedUserVariation(DataModel.FeatureFlag featureFlag, FFCUser user) {
        return featureFlag.getTargets().stream()
                .filter(target -> target.isTargeted(user.getKey()))
                .findFirst()
                .map(target -> EvalResult.of(target.getValueOption(), REASON_TARGET_MATCH, isSendToExperimentForTargetedUserVariation(featureFlag.isExptIncludeAllRules()), featureFlag.getInfo().getKeyName(), featureFlag.getInfo().getName()))
                .orElse(null);
    }

    private EvalResult matchConditionedUserVariation(DataModel.FeatureFlag featureFlag, FFCUser user) {
        DataModel.TargetRule targetRule = featureFlag.getRules().stream().filter(rule -> ifUserMatchRule(user, rule.getRuleJsonContent())).findFirst().orElse(null);
        // optional flatmap can't infer inner type of collection
        return targetRule == null ? null : getRollOutVariationOption(targetRule.getValueOptionsVariationRuleValues(),
                user,
                REASON_RULE_MATCH,
                featureFlag.isExptIncludeAllRules(),
                targetRule.isIncludedInExpt(),
                featureFlag.getInfo().getKeyName(),
                featureFlag.getInfo().getName());
    }

    private boolean ifUserMatchRule(FFCUser user, List<DataModel.RuleItem> clauses) {
        return clauses.stream().allMatch(clause -> ifUserMatchClause(user, clause));
    }

    private boolean ifUserMatchClause(FFCUser user, DataModel.RuleItem clause) {
        String op = clause.getOperation();
        // segment hasn't any operation
        op = StringUtils.isBlank(op) ? clause.getProperty() : op;
        if (op.contains(THAN_CLAUSE)) {
            return thanClause(user, clause);
        } else if (op.equals(EQ_CLAUSE)) {
            return equalsClause(user, clause);
        } else if (op.equals(NEQ_CLAUSE)) {
            return !equalsClause(user, clause);
        } else if (op.equals(CONTAINS_CLAUSE)) {
            return containsClause(user, clause);
        } else if (op.equals(NOT_CONTAIN_CLAUSE)) {
            return !containsClause(user, clause);
        } else if (op.equals(IS_ONE_OF_CLAUSE)) {
            return oneOfClause(user, clause);
        } else if (op.equals(NOT_ONE_OF_CLAUSE)) {
            return !oneOfClause(user, clause);
        } else if (op.equals(STARTS_WITH_CLAUSE)) {
            return startsWithClause(user, clause);
        } else if (op.equals(ENDS_WITH_CLAUSE)) {
            return endsWithClause(user, clause);
        } else if (op.equals(IS_TRUE_CLAUSE)) {
            return trueClause(user, clause);
        } else if (op.equals(IS_FALSE_CLAUSE)) {
            return falseClause(user, clause);
        } else if (op.equals(MATCH_REGEX_CLAUSE)) {
            return matchRegExClause(user, clause);
        } else if (op.equals(NOT_MATCH_REGEX_CLAUSE)) {
            return !matchRegExClause(user, clause);
        } else if (op.equals(IS_IN_SEGMENT_CLAUSE)) {
            return inSegmentClause(user, clause);
        } else if (op.equals(NOT_IN_SEGMENT_CLAUSE)) {
            return !inSegmentClause(user, clause);
        }
        return false;
    }

    private boolean inSegmentClause(FFCUser user, DataModel.RuleItem clause) {
        String pv = user.getKey();
        try {
            List<String> clauseValues = JsonHelper.deserialize(clause.getValue(), new TypeToken<List<String>>() {
            }.getType());
            return clauseValues.stream().map(segmentGetter::get).anyMatch(segment -> {
                if (segment == null) {
                    return false;
                }
                Boolean isInSegment = segment.isMatchUser(pv);
                if (isInSegment == null) {
                    return segment
                            .getRules()
                            .stream()
                            .anyMatch(rule -> ifUserMatchRule(user, rule.getRuleJsonContent()));
                }
                return isInSegment;
            });
        } catch (JsonParseException e) {
            return false;
        }
    }

    private boolean falseClause(FFCUser user, DataModel.RuleItem clause) {
        String pv = user.getProperty(clause.getProperty());
        //TODO add list of false keyword
        return pv != null && pv.equalsIgnoreCase("false");
    }

    private boolean matchRegExClause(FFCUser user, DataModel.RuleItem clause) {
        String pv = user.getProperty(clause.getProperty());
        String clauseValue = clause.getValue();
        return pv != null && Pattern.compile(clauseValue).matcher(pv).matches();
    }

    private boolean trueClause(FFCUser user, DataModel.RuleItem clause) {
        String pv = user.getProperty(clause.getProperty());
        //TODO add list of true keyword
        return pv != null && pv.equalsIgnoreCase("true");
    }

    private boolean endsWithClause(FFCUser user, DataModel.RuleItem clause) {
        String pv = user.getProperty(clause.getProperty());
        String clauseValue = clause.getValue();
        return pv != null && pv.endsWith(clauseValue);
    }

    private boolean startsWithClause(FFCUser user, DataModel.RuleItem clause) {
        String pv = user.getProperty(clause.getProperty());
        String clauseValue = clause.getValue();
        return pv != null && pv.startsWith(clauseValue);
    }

    private boolean thanClause(FFCUser user, DataModel.RuleItem clause) {
        String pv = user.getProperty(clause.getProperty());
        String clauseValue = clause.getValue();
        if (!StringUtils.isNumeric(pv) || !StringUtils.isNumeric(clauseValue)) {
            return false;
        }
        double pvNumber = new BigDecimal(pv).setScale(5, RoundingMode.HALF_UP).doubleValue();
        double cvNumber = new BigDecimal(clauseValue).setScale(5, RoundingMode.HALF_UP).doubleValue();
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

    private boolean equalsClause(FFCUser user, DataModel.RuleItem clause) {
        String pv = user.getProperty(clause.getProperty());
        String clauseValue = clause.getValue();
        return clauseValue.equals(pv);
    }

    private boolean containsClause(FFCUser user, DataModel.RuleItem clause) {
        String pv = user.getProperty(clause.getProperty());
        String clauseValue = clause.getValue();
        return pv != null && pv.contains(clauseValue);
    }

    private boolean oneOfClause(FFCUser user, DataModel.RuleItem clause) {
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
        return getRollOutVariationOption(featureFlag.getInfo().getDefaultRulePercentageRollouts(),
                user,
                REASON_FALLTHROUGH,
                featureFlag.isExptIncludeAllRules(),
                featureFlag.getInfo().isDefaultRulePercentageRolloutsIncludedInExpt(),
                featureFlag.getInfo().getKeyName(),
                featureFlag.getInfo().getName());
    }

    private EvalResult getRollOutVariationOption
            (Collection<DataModel.VariationOptionPercentageRollout> rollouts,
             FFCUser user,
             String reason,
             Boolean exptIncludeAllRules,
             Boolean ruleIncludedInExperiment,
             String flagKeyName,
             String flagName) {
        String newUserKey = Base64.getEncoder().encodeToString(user.getKey().getBytes());
        return rollouts.stream()
                .filter(rollout -> VariationSplittingAlgorithm.ifKeyBelongsPercentage(user.getKey(), rollout.getRolloutPercentage()))
                .findFirst()
                .map(rollout -> EvalResult.of(rollout.getValueOption(), reason, isSendToExperiment(newUserKey, rollout, exptIncludeAllRules, ruleIncludedInExperiment), flagKeyName, flagName))
                .orElse(null);
    }

    private boolean isSendToExperimentForTargetedUserVariation(Boolean exptIncludeAllRules) {
        return exptIncludeAllRules == null || exptIncludeAllRules;
    }

    private boolean isSendToExperiment(String userKey,
                                       DataModel.VariationOptionPercentageRollout rollout,
                                       Boolean exptIncludeAllRules,
                                       Boolean ruleIncludedInExperiment) {
        if (exptIncludeAllRules == null) {
            return true;
        }

        if (ruleIncludedInExperiment == null || rollout.getExptRollout() == null) {
            return true;
        }

        if (!ruleIncludedInExperiment) {
            return false;
        }

        double sendToExperimentPercentage = rollout.getExptRollout();
        double splittingPercentage = rollout.getRolloutPercentage().get(1) - rollout.getRolloutPercentage().get(0);
        if (sendToExperimentPercentage == 0D || splittingPercentage == 0D) {
            return false;
        }

        double upperBound = sendToExperimentPercentage / splittingPercentage;
        if (upperBound > 1D) {
            upperBound = 1D;
        }
        return VariationSplittingAlgorithm.ifKeyBelongsPercentage(userKey, Arrays.asList(0D, upperBound));
    }


}
