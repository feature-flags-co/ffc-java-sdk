package co.featureflags;

import co.featureflags.model.FFCUser;

public interface FFCClient {
    public void reinitialize(String envSecret, FFCUser user, FFCConfig config);

    public String variation(String featureFlagKey, String defaultValue);

    public String variation(String featureFlagKey, FFCUser user, String defaultValue);
}
