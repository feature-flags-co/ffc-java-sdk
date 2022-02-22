package co.featureflags.server.exterior;

import co.featureflags.commons.model.EvalDetail;
import co.featureflags.commons.model.FFCUser;
import co.featureflags.server.Status;

import java.io.Closeable;
import java.util.List;


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
     * Calculates the value of a feature flag for a given user.
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return if the flag should be enabled, or {@code defaultValue} if the flag is disabled or an error occurs
     */
    boolean boolVariation(String featureFlagKey, FFCUser user, Boolean defaultValue);

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
     * Returns an list t of all feature flags value for a given user, including the reason
     * that describes the way the value was determined, that can be used on the client side sdk or a front end .
     * <p>
     * note that this method does not send insight events back to feature-flag.co.
     *
     * @param user the end user requesting the flag
     * @return a list of {@link EvalDetail} object
     */
    List<EvalDetail<String>> getAllLatestFlagsVariations(FFCUser user);

    /**
     * Calculates the value of a feature flag for a given user, and returns an object that describes the
     * way the value was determined.
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return an {@link EvalDetail} object
     */
    EvalDetail<String> variationDetail(String featureFlagKey, FFCUser user, String defaultValue);


    /**
     * Calculates the value of a feature flag for a given user, and returns an object that describes the
     * way the value was determined.
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return an {@link EvalDetail} object
     */
    EvalDetail<Boolean> boolVariationDetail(String featureFlagKey, FFCUser user, Boolean defaultValue);

    /**
     * Calculates the value of a feature flag for a given user, and returns an object that describes the
     * way the value was determined.
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return an {@link EvalDetail} object
     */
    EvalDetail<Double> doubleVariationDetail(String featureFlagKey, FFCUser user, Double defaultValue);

    /**
     * Calculates the value of a feature flag for a given user, and returns an object that describes the
     * way the value was determined.
     * <p>
     * * Note that If the variation has a numeric value, but not a int value, it is rounded toward zero(DOWN mode)
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return an {@link EvalDetail} object
     */
    EvalDetail<Integer> intVariationDetail(String featureFlagKey, FFCUser user, Integer defaultValue);

    /**
     * Calculates the value of a feature flag for a given user, and returns an object that describes the
     * way the value was determined.
     * <p>
     * Note that If the variation has a numeric value, but not a long value, it is rounded toward zero(DOWN mode)
     * <p>
     *
     * @param featureFlagKey the unique key for the feature flag
     * @param user           the end user requesting the flag
     * @param defaultValue   the default value of the flag
     * @return an {@link EvalDetail} object
     */
    EvalDetail<Long> longVariationDetail(String featureFlagKey, FFCUser user, Long defaultValue);
}
