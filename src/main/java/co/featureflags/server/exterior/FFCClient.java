package co.featureflags.server.exterior;

import co.featureflags.commons.model.AllFlagStates;
import co.featureflags.commons.model.FFCUser;
import co.featureflags.commons.model.FlagState;
import co.featureflags.commons.model.UserTag;
import co.featureflags.server.Status;

import java.io.Closeable;
import java.util.List;
import java.util.Map;


/**
 * This interface defines the public methods of {@link co.featureflags.server.FFCClientImp}.
 * <p>
 * Applications will normally interact directly with {@link co.featureflags.server.FFCClientImp}
 * and must use its constructor to initialize the SDK.
 */
public interface FFCClient extends Closeable {
    /**
     * Tests whether the client is ready to be used.
     *
     * @return true if the client is ready, or false if it is still initializing
     */
    boolean isInitialized();

    /**
     * Calculates the value of a feature flag for a given user.
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return the variation for the given user, or {@code defaultValue} if the flag is disabled or an error occurs
     */
    String variation(String featureFlagKey, FFCUser user, String defaultValue);

    /**
     * Calculates the value of a feature flag for current user.
     * <p>
     * note that this method should be called in the context that support to capture automatically the current user
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param defaultValue   the default value of the flag
     * @return the variation for the current user, or {@code defaultValue} if the flag is disabled, current user doesn't exist or an error occurs
     */
    String variation(String featureFlagKey, String defaultValue);

    /**
     * Calculates the boolean value of a feature flag for a given user.
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return if the flag should be enabled, or {@code defaultValue} if the flag is disabled or an error occurs
     */
    boolean boolVariation(String featureFlagKey, FFCUser user, Boolean defaultValue);

    /**
     * Calculates the boolean value of a feature flag for the current user.
     * <p>
     * note that this method should be called in the context that support to capture automatically the current user
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param defaultValue   the default value of the flag
     * @return if the flag should be enabled, or {@code defaultValue} if the flag is disabled, current user doesn't exist or an error occurs
     */
    boolean boolVariation(String featureFlagKey, Boolean defaultValue);

    /**
     * alias of boolVariation for a given user
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @return if the flag should be enabled, or false if the flag is disabled, or an error occurs
     */
    boolean isEnabled(String featureFlagKey, FFCUser user);

    /**
     * alias of boolVariation for a given user
     * <p>
     * note that this method should be called in the context that support to capture automatically the current user
     *
     * @param featureFlag
     * @return if the flag should be enabled, or false if the flag is disabled, current user doesn't exist or an error occurs
     */
    boolean isEnabled(String featureFlag);

    /**
     * Calculates the double value of a feature flag for a given user.
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return the variation for the given user, or {@code defaultValue} if the flag is disabled or an error occurs
     */
    double doubleVariation(String featureFlagKey, FFCUser user, Double defaultValue);

    /**
     * Calculates the double value of a feature flag for the current user.
     * <p>
     * note that this method should be called in the context that support to capture automatically the current user
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param defaultValue   the default value of the flag
     * @return the variation for the current user, or {@code defaultValue} if the flag is disabled, current user doesn't exist or an erro
     */
    double doubleVariation(String featureFlagKey, Double defaultValue);

    /**
     * Calculates the integer value of a feature flag for a given user.
     * Note that If the variation has a numeric value, but not an integer, it is rounded toward zero(DOWN mode)
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return the variation for the given user, or {@code defaultValue} if the flag is disabled or an error occurs
     */
    int intVariation(String featureFlagKey, FFCUser user, Integer defaultValue);

    /**
     * Calculates the integer value of a feature flag for the current user.
     * Note that If the variation has a numeric value, but not an integer, it is rounded toward zero(DOWN mode)
     * <p>
     * note that this method should be called in the context that support to capture automatically the current user
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param defaultValue   the default value of the flag
     * @return the variation for the current user, or {@code defaultValue} if the flag is disabled, current user doesn't exist or an erro
     */
    int intVariation(String featureFlagKey, Integer defaultValue);

    /**
     * Calculates the long value of a feature flag for a given user.
     * Note that If the variation has a numeric value, but not a long value, it is rounded toward zero(DOWN mode)
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return the variation for the given user, or {@code defaultValue} if the flag is disabled or an error occurs
     */
    long longVariation(String featureFlagKey, FFCUser user, Long defaultValue);

    /**
     * Calculates the long value of a feature flag for the current user.
     * Note that If the variation has a numeric value, but not a long value, it is rounded toward zero(DOWN mode)
     * <p>
     * note that this method should be called in the context that support to capture automatically the current user
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param defaultValue   the default value of the flag
     * @return the variation for the current user, or {@code defaultValue} if the flag is disabled, current user doesn't exist or an error
     */
    long longVariation(String featureFlagKey, Long defaultValue);

    /**
     * Returns true if the specified feature flag currently exists.
     *
     * @param featureKey the unique key for the feature flag
     * @return true if the flag exists
     */
    boolean isFlagKnown(String featureKey);

    /**
     * Returns an interface for tracking the status of the update processor.
     * <p>
     * The update processor is the mechanism that the SDK uses to get feature flag, such as a
     * streaming connection. The {@link co.featureflags.server.Status.DataUpdateStatusProvider}
     * is used to check whether the update processor is currently operational
     *
     * @return a {@link co.featureflags.server.Status.DataUpdateStatusProvider}
     */
    Status.DataUpdateStatusProvider getDataUpdateStatusProvider();

    /**
     * initialization in the offline mode
     * <p>
     *
     * @param json feature flags in the json format
     * @return true if the initialization is well done
     * @throws co.featureflags.commons.json.JsonParseException if json is invalid
     */
    boolean initializeFromExternalJson(String json);

    /**
     * Returns a list of all feature flags value with details for a given user, including the reason
     * that describes the way the value was determined, that can be used on the client side sdk or a front end .
     * <p>
     * note that this method does not send insight events back to feature-flag.co.
     *
     * @param user the end user requesting the flag
     * @return a {@link AllFlagStates}
     */
    AllFlagStates<String> getAllLatestFlagsVariations(FFCUser user);

    /**
     * return a list of user tags used to instantiate a {@link FFCUser}
     *
     * @return a list of user tags
     */
    List<UserTag> getAllUserTags();

    /**
     * Calculates the value of a feature flag for a given user, and returns an object that describes the
     * way the value was determined.
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return an {@link FlagState} object
     */
    FlagState<String> variationDetail(String featureFlagKey, FFCUser user, String defaultValue);

    /**
     * Calculates the value of a feature flag for current user, and returns an object that describes the
     * way the value was determined.
     * <p>
     * note that this method should be called in the context that support to capture automatically the current user
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param defaultValue   the default value of the flag
     * @return an {@link FlagState} object
     */
    FlagState<String> variationDetail(String featureFlagKey, String defaultValue);


    /**
     * Calculates the value of a feature flag for a given user, and returns an object that describes the
     * way the value was determined.
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return an {@link FlagState} object
     */
    FlagState<Boolean> boolVariationDetail(String featureFlagKey, FFCUser user, Boolean defaultValue);

    /**
     * Calculates the boolean value of a feature flag for the current user, and returns an object that describes the
     * way the value was determined.
     * <p>
     * note that this method should be called in the context that support to capture automatically the current user
     *
     * @param featureFlagKey
     * @param defaultValue
     * @return an {@link FlagState} object
     */
    FlagState<Boolean> boolVariationDetail(String featureFlagKey, Boolean defaultValue);

    /**
     * Calculates the double value of a feature flag for a given user, and returns an object that describes the
     * way the value was determined.
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return an {@link FlagState} object
     */
    FlagState<Double> doubleVariationDetail(String featureFlagKey, FFCUser user, Double defaultValue);

    /**
     * Calculates the double value of a feature flag for a given user, and returns an object that describes the
     * way the value was determined.
     * <p>
     * note that this method should be called in the context that support to capture automatically the current user
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param defaultValue   the default value of the flag
     * @return an {@link FlagState} object
     */
    FlagState<Double> doubleVariationDetail(String featureFlagKey, Double defaultValue);

    /**
     * Calculates the int value of a feature flag for a given user, and returns an object that describes the
     * way the value was determined.
     * <p>
     * Note that If the variation has a numeric value, but not a int value, it is rounded toward zero(DOWN mode)
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return an {@link FlagState} object
     */
    FlagState<Integer> intVariationDetail(String featureFlagKey, FFCUser user, Integer defaultValue);

    /**
     * Calculates the int value of a feature flag for a given user, and returns an object that describes the
     * way the value was determined.
     * <p>
     * Note that If the variation has a numeric value, but not a int value, it is rounded toward zero(DOWN mode)
     * <p>
     * note that this method should be called in the context that support to capture automatically the current user
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param defaultValue   the default value of the flag
     * @return an {@link FlagState} object
     */
    FlagState<Integer> intVariationDetail(String featureFlagKey, Integer defaultValue);

    /**
     * Calculates the long value of a feature flag for a given user, and returns an object that describes the
     * way the value was determined.
     * <p>
     * Note that If the variation has a numeric value, but not a long value, it is rounded toward zero(DOWN mode)
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the unique key for the feature flag
     * @return an {@link FlagState} object
     */
    FlagState<Long> longVariationDetail(String featureFlagKey, FFCUser user, Long defaultValue);

    /**
     * Calculates the long of a feature flag for a given user, and returns an object that describes the
     * way the value was determined.
     * <p>
     * Note that If the variation has a numeric value, but not a long value, it is rounded toward zero(DOWN mode)
     * <p>
     * note that this method should be called in the context that support to capture automatically the current user
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param defaultValue   the unique key for the feature flag
     * @return an {@link FlagState} object
     */
    FlagState<Long> longVariationDetail(String featureFlagKey, Long defaultValue);

    /**
     * Flushes all pending events.
     */
    void flush();

    /**
     * tracks that a user performed an event and provides a default numeric value for custom metrics
     *
     * @param user      the user that performed the event
     * @param eventName the name of the event
     */
    void trackMetric(FFCUser user, String eventName);

    /**
     * tracks that the current user performed an event and provides a default numeric value for custom metrics
     * <p>
     * note that this method should be called in the context that support to capture automatically the current user
     *
     * @param eventName the name of the event
     */
    void trackMetric(String eventName);

    /**
     * tracks that a user performed an event, and provides an additional numeric value for custom metrics.
     *
     * @param user        the user that performed the event
     * @param eventName   the name of the event
     * @param metricValue a numeric value used by the experimentation feature in numeric custom metrics.
     */
    void trackMetric(FFCUser user, String eventName, double metricValue);

    /**
     * tracks that the current user performed an event, and provides an additional numeric value for custom metrics.
     * <p>
     * note that this method should be called in the context that support to capture automatically the current user
     *
     * @param eventName   the name of the event
     * @param metricValue a numeric value used by the experimentation feature in numeric custom metrics.
     */
    void trackMetric(String eventName, double metricValue);

    /**
     * tracks that a user performed a series of events with default numeric value for custom metrics
     *
     * @param user       the user that performed the event
     * @param eventNames event names
     */
    void trackMetrics(FFCUser user, String... eventNames);

    /**
     * tracks that the current user performed a series of events with default numeric value for custom metrics
     * <p>
     * note that this method should be called in the context that support to capture automatically the current user
     *
     * @param eventNames event names
     */
    void trackMetrics(String... eventNames);

    /**
     * tracks that a user performed a series of events
     *
     * @param user    the user that performed the event
     * @param metrics event name and numeric value in K/V
     */
    void trackMetrics(FFCUser user, Map<String, Double> metrics);

    /**
     * tracks that the current user performed a series of events
     * <p>
     * note that this method should be called in the context that support to capture automatically the current user
     *
     * @param metrics event name and numeric value in K/V
     */
    void trackMetrics(Map<String, Double> metrics);
}
