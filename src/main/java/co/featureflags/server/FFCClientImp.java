package co.featureflags.server;

import co.featureflags.server.exterior.DataStorage;
import co.featureflags.server.exterior.DataStoreTypes;
import co.featureflags.server.exterior.FFCClient;
import co.featureflags.server.exterior.UpdateProcessor;
import co.featureflags.server.exterior.model.FFCUser;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static co.featureflags.server.Evaluator.REASON_CLIENT_NOT_READY;
import static co.featureflags.server.Evaluator.REASON_ERROR;
import static co.featureflags.server.Evaluator.REASON_FLAG_NOT_FOUND;
import static co.featureflags.server.Evaluator.REASON_USER_NOT_SPECIFIED;
import static co.featureflags.server.Evaluator.REASON_WRONG_TYPE;
import static co.featureflags.server.exterior.DataStoreTypes.FEATURES;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class FFCClientImp implements FFCClient {

    private final static Logger logger = Loggers.CLIENT;

    private final String envSecret;
    private final boolean offline;
    private final DataStorage storage;
    private final Evaluator evaluator;
    private final UpdateProcessor updateProcessor;

    public FFCClientImp(String envSecret, FFCConfig config) {
        checkNotNull(config, "FFCConfig Should not be null");
        this.offline = config.isOffline();
        checkArgument(Base64.isBase64(envSecret), "envSecret is invalid");
        this.envSecret = envSecret;
        ContextImp context = new ContextImp(envSecret, config);
        this.storage = config.getDataStorageFactory().createDataStorage(context);
        Evaluator.Getter<DataModel.FeatureFlag> flagGetter = key -> {
            DataStoreTypes.Item item = this.storage.get(FEATURES, key);
            return item == null ? null : (DataModel.FeatureFlag) item.item();
        };
        this.evaluator = new EvaluatorImp(flagGetter);
        this.updateProcessor = config.getUpdateProcessorFactory().createUpdateProcessor(context, this.storage);

        try {
            // data sync
            Duration startWaitTime = config.getStartWaitTime();
            Future<Boolean> initFuture = this.updateProcessor.start();
            if (!(config.getUpdateProcessorFactory() instanceof FactoryImp.NullUpdateProcessorFactory)) {
                logger.info(String.format("Waiting for data update in %d milliseconds", startWaitTime.toMillis()));
            }
            if (config.getDataStorageFactory() instanceof FactoryImp.NullDataStorageFactory) {
                logger.info("JAVA SDK Client just return default variation");
            }
            boolean initResult = (startWaitTime.isZero() || startWaitTime.isNegative()) ? initFuture.get() :
                    initFuture.get(startWaitTime.toMillis(), TimeUnit.MILLISECONDS);
            if (initResult) {
                logger.info("JAVA SDK Client initialization completed");
            }
        } catch (TimeoutException e) {
            logger.error("Timeout encountered waiting for data update");
        } catch (Exception e) {
            logger.error("Exception encountered waiting for data update", e);
        }

        if (!this.storage.isInitialized()) {
            logger.info("BUT JAVA SDK Client was not successfully initialized");
        }

    }

    public boolean isInitialized() {
        return updateProcessor.isInitialized();
    }

    public String variation(String featureFlagKey, FFCUser user, String defaultValue) {
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, false);
        return res.getValue();
    }

    public boolean boolVariation(String featureFlagKey, FFCUser user, Boolean defaultValue) {
        checkNotNull(defaultValue, "null is invalid");
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, true);
        return BooleanUtils.toBoolean(res.getValue());
    }

    public double doubleVariation(String featureFlagKey, FFCUser user, Double defaultValue) {
        checkNotNull(defaultValue, "null is invalid");
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, true);
        return Double.parseDouble(res.getValue());
    }


    public int intVariation(String featureFlagKey, FFCUser user, Integer defaultValue) {
        checkNotNull(defaultValue, "null is invalid");
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, true);
        return Double.valueOf(res.getValue()).intValue();
    }

    public long longVariation(String featureFlagKey, FFCUser user, Long defaultValue) {
        checkNotNull(defaultValue, "null is invalid");
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, true);
        return Double.valueOf(res.getValue()).longValue();
    }


    Evaluator.EvalResult evaluateInternal(String featureFlagKey, FFCUser user, Object defaultValue, boolean checkType) {
        try {
            if (!isInitialized()) {
                Loggers.EVALUATION.warn("Evaluation called before Java SDK client initialized for feature flag, well using the default value");
                return Evaluator.EvalResult.error(defaultValue.toString(), REASON_CLIENT_NOT_READY);
            }
            if (StringUtils.isBlank(featureFlagKey)) {
                Loggers.EVALUATION.info("null feature flag key; returning default value");
                return Evaluator.EvalResult.error(defaultValue.toString(), REASON_FLAG_NOT_FOUND);
            }
            DataModel.FeatureFlag flag = getFlagInternal(featureFlagKey);
            if (flag == null) {
                Loggers.EVALUATION.info(String.format("Unknown feature flag %s; returning default value", featureFlagKey));
                return Evaluator.EvalResult.error(defaultValue.toString(), REASON_FLAG_NOT_FOUND);
            }
            if (user == null || StringUtils.isBlank(user.getKey())) {
                Loggers.EVALUATION.info(String.format("Null user for feature flag %s, returning default value", featureFlagKey));
                return Evaluator.EvalResult.error(defaultValue.toString(), REASON_USER_NOT_SPECIFIED);
            }
            Evaluator.EvalResult res = evaluator.evaluate(flag, user);
            if (checkType && !res.checkType(defaultValue)) {
                Loggers.EVALUATION.info(String.format("evaluation result %s didn't matched expected type ", res.getValue()));
                return Evaluator.EvalResult.error(defaultValue.toString(), REASON_WRONG_TYPE);
            }
            return res;
        } catch (Exception ex) {
            logger.error("unexpected error in evaluation", ex);
            return Evaluator.EvalResult.error(defaultValue.toString(), REASON_ERROR);
        }

    }

    private DataModel.FeatureFlag getFlagInternal(String featureFlagKey) {
        String flagId = FeatureFlagKeyExtension.FeatureFlagIdByEnvSecret
                .of(envSecret, featureFlagKey)
                .getFeatureFlagId();
        DataStoreTypes.Item item = storage.get(FEATURES, flagId);
        return item == null ? null : (DataModel.FeatureFlag) item.item();
    }

    public boolean isFlagKnown(String featureKey) {
        try {
            if (!isInitialized()) {
                logger.warn("isFlagKnown called before Java SDK client initialized for feature flag");
                return false;
            }
            return getFlagInternal(featureKey) == null;
        } catch (Exception ex) {
            logger.error("unexpected error in isFlagKnown", ex);
        }
        return false;

    }


    public void close() throws IOException {
        logger.info("Java SDK client is closing...");
        this.storage.close();
        this.updateProcessor.close();
    }

    public boolean isOffline() {
        return offline;
    }

}
