package co.featureflags;

import co.featureflags.model.FFCUser;

public interface FFCClient {
    public void initialize(String envSecret, FFCConfig config);

    public String variation(String featureFlagKey, FFCUser user, String defaultValue);
}
