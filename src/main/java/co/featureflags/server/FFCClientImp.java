package co.featureflags.server;

import co.featureflags.commons.json.JsonHelper;
import co.featureflags.commons.model.AllFlagStates;
import co.featureflags.commons.model.EvalDetail;
import co.featureflags.commons.model.FFCUser;
import co.featureflags.commons.model.FlagState;
import co.featureflags.commons.model.UserTag;
import co.featureflags.server.exterior.DataStorage;
import co.featureflags.server.exterior.DataStoreTypes;
import co.featureflags.server.exterior.FFCClient;
import co.featureflags.server.exterior.InsightProcessor;
import co.featureflags.server.exterior.UpdateProcessor;
import co.featureflags.server.integrations.FFCUserContextHolder;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static co.featureflags.server.Evaluator.FLAG_KEY_UNKNOWN;
import static co.featureflags.server.Evaluator.FLAG_NAME_UNKNOWN;
import static co.featureflags.server.Evaluator.FLAG_VALUE_UNKNOWN;
import static co.featureflags.server.Evaluator.NO_EVAL_RES;
import static co.featureflags.server.Evaluator.REASON_CLIENT_NOT_READY;
import static co.featureflags.server.Evaluator.REASON_ERROR;
import static co.featureflags.server.Evaluator.REASON_FLAG_NOT_FOUND;
import static co.featureflags.server.Evaluator.REASON_USER_NOT_SPECIFIED;
import static co.featureflags.server.Evaluator.REASON_WRONG_TYPE;
import static co.featureflags.server.exterior.DataStoreTypes.FEATURES;
import static co.featureflags.server.exterior.DataStoreTypes.SEGMENTS;
import static co.featureflags.server.exterior.DataStoreTypes.USERTAGS;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A client for the feature-flag.co API. The client is thread-safe.
 */
public final class FFCClientImp implements FFCClient {

    private final static Logger logger = Loggers.CLIENT;

    private final String envSecret;
    private final boolean offline;
    private final DataStorage storage;
    private final Evaluator evaluator;
    private final UpdateProcessor updateProcessor;
    private final Status.DataUpdateStatusProvider dataUpdateStatusProvider;
    private final Status.DataUpdator dataUpdator;
    private final InsightProcessor insightProcessor;

    private final Consumer<InsightTypes.Event> eventHandler;

    /**
     * Creates a new client to connect to feature-flag.co with a specified configuration.
     * <p>
     * Applications SHOULD instantiate a single instance for the lifetime of the application. In
     * the case where an application needs to evaluate feature flags from different environments,
     * you may create multiple clients, but they should still be retained
     * for the lifetime of the application rather than created per request or per thread.
     * <p>
     * The client try to connect to feature-flag.co as soon as the constructor is called. The constructor will return
     * when it successfully connects, or when the timeout (15 seconds) expires, whichever comes first.
     * If it has not succeeded in connecting when the timeout elapses, you will receive the client in an uninitialized state
     * where feature flags will return default values; it will still continue trying to connect in the background
     * unless there has been an {@link java.net.ProtocolException} or you close the client{@link #close()}.
     * You can detect whether initialization has succeeded by calling {@link #isInitialized()}.
     *
     * @param envSecret the secret key for your own environment
     * @throws IllegalArgumentException if envSecret is invalid
     */
    public FFCClientImp(String envSecret) {
        this(envSecret, FFCConfig.DEFAULT);
    }

    /**
     * Creates a new client to connect to feature-flag.co with a specified configuration.
     * <p>
     * This constructor can be used to configure advanced SDK features; see {@link FFCConfig.Builder}.
     * <p>
     * Applications SHOULD instantiate a single instance for the lifetime of the application. In
     * the case where an application needs to evaluate feature flags from different environments,
     * you may create multiple clients, but they should still be retained
     * for the lifetime of the application rather than created per request or per thread.
     * <p>
     * Note that unless client is configured in offline mode{@link FFCConfig.Builder#offline(boolean)} or set by
     * {@link Factory#externalOnlyDataUpdate()}, this client try to connect to feature-flag.co
     * as soon as the constructor is called. The constructor will return when it successfully
     * connects, or when the timeout set by {@link FFCConfig.Builder#startWaitTime(java.time.Duration)} (default:
     * 15 seconds) expires, whichever comes first. If it has not succeeded in connecting when the timeout
     * elapses, you will receive the client in an uninitialized state where feature flags will return
     * default values; it will still continue trying to connect in the background unless there has been an {@link java.net.ProtocolException}
     * or you close the client{@link #close()}. You can detect whether initialization has succeeded by calling {@link #isInitialized()}.
     * <p>
     * If you prefer to have the constructor return immediately, and then wait for initialization to finish
     * at some other point, you can use {@link #getDataUpdateStatusProvider()} as follows:
     * <pre><code>
     *     FFCConfig config = new FFCConfig.Builder()
     *         .startWait(Duration.ZERO)
     *         .build();
     *     FFCClient client = new FFCClientImp(sdkKey, config);
     *
     *     // later, when you want to wait for initialization to finish:
     *     boolean inited = client.getDataUpdateStatusProvider().waitForOKState(Duration.ofSeconds(15))
     *     if (!inited) {
     *         // do whatever is appropriate if initialization has timed out
     *     }
     * </code></pre>
     * <p>
     * This constructor can throw unchecked exceptions if it is immediately apparent that
     * the SDK cannot work with these parameters. In fact, if the env secret is not valid,
     * it will throw an {@link IllegalArgumentException}  a null value for a non-nullable
     * parameter may throw a {@link NullPointerException}. The constructor will not throw
     * any exception that could only be detected after making a request to our API
     *
     * @param envSecret the secret key for your own environment
     * @param config    a client configuration object {@link FFCConfig}
     * @throws NullPointerException     if a non-nullable parameter was null
     * @throws IllegalArgumentException if envSecret is invalid
     */
    public FFCClientImp(String envSecret, FFCConfig config) {
        checkNotNull(config, "FFCConfig Should not be null");
        this.offline = config.isOffline();
        checkArgument(Base64.isBase64(envSecret), "envSecret is invalid");
        this.envSecret = envSecret;
        ContextImp context = new ContextImp(envSecret, config);
        //init components
        //Insight processor
        this.insightProcessor = config.getInsightProcessorFactory().createInsightProcessor(context);
        this.eventHandler = event -> this.insightProcessor.send(event);
        //data storage
        this.storage = config.getDataStorageFactory().createDataStorage(context);
        //evaluator
        Evaluator.Getter<DataModel.FeatureFlag> flagGetter = key -> {
            DataStoreTypes.Item item = this.storage.get(FEATURES, key);
            return item == null ? null : (DataModel.FeatureFlag) item.item();
        };
        Evaluator.Getter<DataModel.Segment> segmentGetter = key -> {
            DataStoreTypes.Item item = this.storage.get(SEGMENTS, key);
            return item == null ? null : (DataModel.Segment) item.item();
        };
        this.evaluator = new EvaluatorImp(flagGetter, segmentGetter);
        //data updator
        Status.DataUpdatorImpl dataUpdatorImpl = new Status.DataUpdatorImpl(this.storage);
        this.dataUpdator = dataUpdatorImpl;
        //data processor
        this.updateProcessor = config.getUpdateProcessorFactory().createUpdateProcessor(context, dataUpdatorImpl);
        //data update status provider
        this.dataUpdateStatusProvider = new Status.DataUpdateStatusProviderImpl(dataUpdatorImpl);

        // data sync
        Duration startWait = config.getStartWaitTime();
        Future<Boolean> initFuture = this.updateProcessor.start();
        if (!startWait.isZero() && !startWait.isNegative()) {
            try {
                if (!(config.getUpdateProcessorFactory() instanceof FactoryImp.NullUpdateProcessorFactory)) {
                    logger.info("FFC JAVA SDK: waiting for Client initialization in {} milliseconds", startWait.toMillis());
                }
                if (config.getDataStorageFactory() instanceof FactoryImp.NullDataStorageFactory) {
                    logger.info("FFC JAVA SDK: SDK just returns default variation");
                }
                boolean initResult = initFuture.get(startWait.toMillis(), TimeUnit.MILLISECONDS);
                if (initResult && !offline) {
                    logger.info("FFC JAVA SDK: the initialization completed");
                }
            } catch (TimeoutException e) {
                logger.error("FFC JAVA SDK: timeout encountered when waiting for data update");
            } catch (Exception e) {
                logger.error("FFC JAVA SDK: exception encountered when waiting for data update", e);
            }

            if (!this.storage.isInitialized() && !offline) {
                logger.info("FFC JAVA SDK: SDK was not successfully initialized");
            }
        }
    }

    @Override
    public boolean isInitialized() {
        return updateProcessor.isInitialized();
    }

    @Override
    public String variation(String featureFlagKey, FFCUser user, String defaultValue) {
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, false);
        return res.getValue();
    }

    @Override
    public String variation(String featureFlagKey, String defaultValue) {
        return variation(featureFlagKey, FFCUserContextHolder.getCurrentUser(), defaultValue);
    }

    @Override
    public FlagState<String> variationDetail(String featureFlagKey, FFCUser user, String defaultValue) {
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, false);
        return EvalDetail.of(res.getValue(), res.getIndex(), res.getReason(), featureFlagKey, featureFlagKey).toFlagState();
    }

    @Override
    public FlagState<String> variationDetail(String featureFlagKey, String defaultValue) {
        return variationDetail(featureFlagKey, FFCUserContextHolder.getCurrentUser(), defaultValue);
    }

    @Override
    public boolean boolVariation(String featureFlagKey, FFCUser user, Boolean defaultValue) {
        checkNotNull(defaultValue, "null defaultValue is invalid");
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, true);
        return BooleanUtils.toBoolean(res.getValue());
    }

    @Override
    public boolean boolVariation(String featureFlagKey, Boolean defaultValue) {
        return boolVariation(featureFlagKey, FFCUserContextHolder.getCurrentUser(), defaultValue);
    }

    @Override
    public boolean isEnabled(String featureFlagKey, FFCUser user) {
        return boolVariation(featureFlagKey, user, false);
    }

    @Override
    public boolean isEnabled(String featureFlagKey) {
        return boolVariation(featureFlagKey, FFCUserContextHolder.getCurrentUser(), false);
    }

    @Override
    public FlagState<Boolean> boolVariationDetail(String featureFlagKey, FFCUser user, Boolean defaultValue) {
        checkNotNull(defaultValue, "null defaultValue is invalid");
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, true);
        return EvalDetail.of(BooleanUtils.toBoolean(res.getValue()), res.getIndex(), res.getReason(), featureFlagKey, featureFlagKey).toFlagState();
    }

    @Override
    public FlagState<Boolean> boolVariationDetail(String featureFlagKey, Boolean defaultValue) {
        return boolVariationDetail(featureFlagKey, FFCUserContextHolder.getCurrentUser(), defaultValue);
    }

    public double doubleVariation(String featureFlagKey, FFCUser user, Double defaultValue) {
        checkNotNull(defaultValue, "null defaultValue is invalid");
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, true);
        return Double.parseDouble(res.getValue());
    }

    @Override
    public double doubleVariation(String featureFlagKey, Double defaultValue) {
        return doubleVariation(featureFlagKey, FFCUserContextHolder.getCurrentUser(), defaultValue);
    }


    @Override
    public FlagState<Double> doubleVariationDetail(String featureFlagKey, Double defaultValue) {
        return doubleVariationDetail(featureFlagKey, FFCUserContextHolder.getCurrentUser(), defaultValue);
    }

    @Override
    public FlagState<Double> doubleVariationDetail(String featureFlagKey, FFCUser user, Double defaultValue) {
        checkNotNull(defaultValue, "null defaultValue is invalid");
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, true);
        return EvalDetail.of(Double.parseDouble(res.getValue()), res.getIndex(), res.getReason(), featureFlagKey, featureFlagKey).toFlagState();
    }

    public int intVariation(String featureFlagKey, FFCUser user, Integer defaultValue) {
        checkNotNull(defaultValue, "null defaultValue is invalid");
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, true);
        return Double.valueOf(res.getValue()).intValue();
    }

    @Override
    public int intVariation(String featureFlagKey, Integer defaultValue) {
        return intVariation(featureFlagKey, defaultValue);
    }

    @Override
    public FlagState<Integer> intVariationDetail(String featureFlagKey, FFCUser user, Integer defaultValue) {
        checkNotNull(defaultValue, "null defaultValue is invalid");
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, true);
        return EvalDetail.of(Double.valueOf(res.getValue()).intValue(), res.getIndex(), res.getReason(), featureFlagKey, featureFlagKey).toFlagState();
    }

    @Override
    public FlagState<Integer> intVariationDetail(String featureFlagKey, Integer defaultValue) {
        return intVariationDetail(featureFlagKey, FFCUserContextHolder.getCurrentUser(), defaultValue);
    }

    public long longVariation(String featureFlagKey, FFCUser user, Long defaultValue) {
        checkNotNull(defaultValue, "null defaultValue is invalid");
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, true);
        return Double.valueOf(res.getValue()).longValue();
    }

    @Override
    public long longVariation(String featureFlagKey, Long defaultValue) {
        return longVariation(featureFlagKey, FFCUserContextHolder.getCurrentUser(), defaultValue);
    }

    @Override
    public FlagState<Long> longVariationDetail(String featureFlagKey, FFCUser user, Long defaultValue) {
        checkNotNull(defaultValue, "null defaultValue is invalid");
        Evaluator.EvalResult res = evaluateInternal(featureFlagKey, user, defaultValue, true);
        return EvalDetail.of(Double.valueOf(res.getValue()).longValue(), res.getIndex(), res.getReason(), featureFlagKey, featureFlagKey).toFlagState();
    }

    @Override
    public FlagState<Long> longVariationDetail(String featureFlagKey, Long defaultValue) {
        return longVariationDetail(featureFlagKey, FFCUserContextHolder.getCurrentUser(), defaultValue);
    }

    Evaluator.EvalResult evaluateInternal(String featureFlagKey, FFCUser user, Object defaultValue, boolean checkType) {
        try {
            if (!isInitialized()) {
                Loggers.EVALUATION.warn("FFC JAVA SDK: evaluation is called before Java SDK client is initialized for feature flag, well using the default value");
                return Evaluator.EvalResult.error(defaultValue.toString(), REASON_CLIENT_NOT_READY, featureFlagKey, FLAG_NAME_UNKNOWN);
            }
            if (StringUtils.isBlank(featureFlagKey)) {
                Loggers.EVALUATION.warn("FFC JAVA SDK: null feature flag key; returning default value");
                return Evaluator.EvalResult.error(defaultValue.toString(), REASON_FLAG_NOT_FOUND, featureFlagKey, FLAG_NAME_UNKNOWN);
            }
            DataModel.FeatureFlag flag = getFlagInternal(featureFlagKey);
            if (flag == null) {
                Loggers.EVALUATION.warn("FFC JAVA SDK: unknown feature flag {}; returning default value", featureFlagKey);
                return Evaluator.EvalResult.error(defaultValue.toString(), REASON_FLAG_NOT_FOUND, featureFlagKey, FLAG_NAME_UNKNOWN);
            }
            if (user == null || StringUtils.isBlank(user.getKey())) {
                Loggers.EVALUATION.warn("FFC JAVA SDK: null user for feature flag {}, returning default value", featureFlagKey);
                return Evaluator.EvalResult.error(defaultValue.toString(), REASON_USER_NOT_SPECIFIED, featureFlagKey, FLAG_NAME_UNKNOWN);
            }

            InsightTypes.Event event = InsightTypes.FlagEvent.of(user);
            Evaluator.EvalResult res = evaluator.evaluate(flag, user, event);
            if (checkType && !res.checkType(defaultValue)) {
                Loggers.EVALUATION.warn("FFC JAVA SDK: evaluation result {} didn't matched expected type ", res.getValue());
                return Evaluator.EvalResult.error(defaultValue.toString(), REASON_WRONG_TYPE, res.getKeyName(), res.getName());
            }
            eventHandler.accept(event);
            return res;
        } catch (Exception ex) {
            logger.error("FFC JAVA SDK: unexpected error in evaluation", ex);
            return Evaluator.EvalResult.error(defaultValue.toString(), REASON_ERROR, featureFlagKey, FLAG_NAME_UNKNOWN);
        }

    }

    private DataModel.FeatureFlag getFlagInternal(String featureFlagKey) {
        String flagId = FeatureFlagKeyExtension.FeatureFlagIdByEnvSecret.of(envSecret, featureFlagKey).getFeatureFlagId();
        DataStoreTypes.Item item = storage.get(FEATURES, flagId);
        return item == null ? null : (DataModel.FeatureFlag) item.item();
    }

    public boolean isFlagKnown(String featureKey) {
        try {
            if (!isInitialized()) {
                logger.warn("FFC JAVA SDK: isFlagKnown is called before Java SDK client is initialized for feature flag");
                return false;
            }
            return getFlagInternal(featureKey) == null;
        } catch (Exception ex) {
            logger.error("FFC JAVA SDK: unexpected error in isFlagKnown", ex);
        }
        return false;

    }


    public void close() throws IOException {
        logger.info("FFC JAVA SDK: Java SDK client is closing...");
        this.storage.close();
        this.updateProcessor.close();
        this.insightProcessor.close();
    }

    public boolean isOffline() {
        return offline;
    }

    @Override
    public Status.DataUpdateStatusProvider getDataUpdateStatusProvider() {
        return dataUpdateStatusProvider;
    }

    @Override
    public boolean initializeFromExternalJson(String json) {
        if (offline && StringUtils.isNotBlank(json)) {
            DataModel.All all = JsonHelper.deserialize(json, DataModel.All.class);
            if (all.isProcessData()) {
                DataModel.Data allData = all.data();
                Long version = allData.getTimestamp();
                Map<DataStoreTypes.Category, Map<String, DataStoreTypes.Item>> allDataInStorageType = allData.toStorageType();
                boolean res = dataUpdator.init(allDataInStorageType, version);
                if (res) {
                    dataUpdator.updateStatus(Status.StateType.OK, null);
                }
                return res;
            }
        }
        return false;
    }

    @Override
    public AllFlagStates<String> getAllLatestFlagsVariations(FFCUser user) {
        ImmutableMap.Builder<EvalDetail<String>, InsightTypes.Event> builder = ImmutableMap.builder();
        boolean success = true;
        String errorString = null;
        EvalDetail ed;
        try {
            if (!isInitialized()) {
                Loggers.EVALUATION.warn("FFC JAVA SDK: Evaluation is called before Java SDK client is initialized for feature flag");
                ed = EvalDetail.of(FLAG_VALUE_UNKNOWN,
                        NO_EVAL_RES,
                        REASON_CLIENT_NOT_READY,
                        FLAG_KEY_UNKNOWN,
                        FLAG_NAME_UNKNOWN);
                builder.put(ed, null);
                success = false;
                errorString = REASON_CLIENT_NOT_READY;
            } else if (user == null || StringUtils.isBlank(user.getKey())) {
                Loggers.EVALUATION.warn("FFC JAVA SDK: null user or feature flag");
                ed = EvalDetail.of(FLAG_VALUE_UNKNOWN,
                        NO_EVAL_RES,
                        REASON_USER_NOT_SPECIFIED,
                        FLAG_KEY_UNKNOWN,
                        FLAG_NAME_UNKNOWN);
                builder.put(ed, null);
                success = false;
                errorString = REASON_USER_NOT_SPECIFIED;
            } else {
                Map<String, DataStoreTypes.Item> allFlags = this.storage.getAll(FEATURES);
                for (DataStoreTypes.Item item : allFlags.values()) {
                    InsightTypes.Event event = InsightTypes.FlagEvent.of(user);
                    DataModel.FeatureFlag flag = (DataModel.FeatureFlag) item.item();
                    Evaluator.EvalResult res = evaluator.evaluate(flag, user, event);
                    ed = EvalDetail.of(res.getValue(),
                            res.getIndex(),
                            res.getReason(),
                            res.getKeyName(),
                            res.getName());
                    builder.put(ed, event);
                }
            }
        } catch (Exception ex) {
            logger.error("FFC JAVA SDK: unexpected error in evaluation", ex);
            ed = EvalDetail.of(FLAG_VALUE_UNKNOWN,
                    NO_EVAL_RES,
                    REASON_ERROR,
                    FLAG_KEY_UNKNOWN,
                    FLAG_NAME_UNKNOWN);
            builder.put(ed, null);
            success = false;
            errorString = REASON_ERROR;
        }
        return new Implicits.ComplexAllFlagStates<>(success, errorString, builder.build(), eventHandler);
    }

    @Override
    public List<UserTag> getAllUserTags() {
        ImmutableList.Builder<UserTag> tags = new ImmutableList.Builder<>();
        if (isInitialized()) {
            Map<String, DataStoreTypes.Item> items = this.storage.getAll(USERTAGS);
            for (DataStoreTypes.Item item : items.values()) {
                tags.add((UserTag) item.item());
            }
        }
        return tags.build();
    }

    @Override
    public void flush() {
        this.insightProcessor.flush();
    }

    @Override
    public void trackMetric(FFCUser user, String eventName) {
        trackMetric(user, eventName, 1.0);
    }

    @Override
    public void trackMetric(String eventName) {
        trackMetric(FFCUserContextHolder.getCurrentUser(), eventName, 1.0);
    }

    @Override
    public void trackMetric(FFCUser user, String eventName, double metricValue) {
        if (user == null || StringUtils.isBlank(eventName) || metricValue <= 0) {
            Loggers.CLIENT.warn("FFC JAVA SDK: event/user/metric invalid");
            return;
        }
        InsightTypes.Event event = InsightTypes.MetricEvent.of(user)
                .add(InsightTypes.Metric.of(eventName, metricValue));
        insightProcessor.send(event);
    }

    @Override
    public void trackMetric(String eventName, double metricValue) {
        trackMetric(FFCUserContextHolder.getCurrentUser(), eventName, metricValue);
    }

    @Override
    public void trackMetrics(FFCUser user, String... eventNames) {
        if (user == null || eventNames == null || eventNames.length == 0) {
            Loggers.CLIENT.warn("FFC JAVA SDK: user/events invalid");
            return;
        }
        InsightTypes.Event event = InsightTypes.MetricEvent.of(user);
        for (String eventName : eventNames) {
            if (StringUtils.isNotBlank(eventName)) {
                event.add(InsightTypes.Metric.of(eventName, 1.0));
            }
        }
        insightProcessor.send(event);
    }

    @Override
    public void trackMetrics(String... eventNames) {
        trackMetrics(FFCUserContextHolder.getCurrentUser(), eventNames);
    }

    @Override
    public void trackMetrics(FFCUser user, Map<String, Double> metrics) {
        if (user == null || metrics == null || metrics.isEmpty()) {
            Loggers.CLIENT.warn("FFC JAVA SDK: user/metrics invalid");
            return;
        }
        InsightTypes.Event event = InsightTypes.MetricEvent.of(user);
        for (Map.Entry<String, Double> entry : metrics.entrySet()) {
            String eventName = entry.getKey();
            Double metricValue = entry.getValue();
            if (StringUtils.isNotBlank(eventName) && metricValue != null && metricValue > 0D) {
                event.add(InsightTypes.Metric.of(eventName, metricValue));
            }
        }
        insightProcessor.send(event);
    }

    @Override
    public void trackMetrics(Map<String, Double> metrics) {
        trackMetrics(FFCUserContextHolder.getCurrentUser(), metrics);
    }
}
