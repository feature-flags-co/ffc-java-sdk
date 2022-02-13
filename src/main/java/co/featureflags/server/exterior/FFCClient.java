package co.featureflags.server.exterior;

import co.featureflags.server.exterior.model.FFCUser;

import java.io.Closeable;

public interface FFCClient extends Closeable {
    public boolean isInitialized();

    public String variation(String featureFlagKey, FFCUser user, String defaultValue);

    public boolean boolVariation(String featureFlagKey, FFCUser user, Boolean defaultValue);

    public double doubleVariation(String featureFlagKey, FFCUser user, Double defaultValue);

    public int intVariation(String featureFlagKey, FFCUser user, Integer defaultValue);

    public long longVariation(String featureFlagKey, FFCUser user, Long defaultValue);

    public boolean isFlagKnown(String featureKey);
}
