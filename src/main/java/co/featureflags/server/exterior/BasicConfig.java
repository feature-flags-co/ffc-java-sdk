package co.featureflags.server.exterior;

/**
 * the basic configuration of SDK that will be used for all components
 */
public final class BasicConfig {

    private final String envSecret;
    private final boolean offline;

    /**
     * constructs an instance
     *
     * @param envSecret the env secret of your env
     * @param offline   true if the SDK was configured to be completely offline
     */
    public BasicConfig(String envSecret, boolean offline) {
        this.envSecret = envSecret;
        this.offline = offline;
    }

    /**
     * return the env secret
     * @return a string
     */
    public String getEnvSecret() {
        return envSecret;
    }

    /**
     * Returns true if the client was configured to be completely offline.
     * @return true if offline
     */
    public boolean isOffline() {
        return offline;
    }
}
