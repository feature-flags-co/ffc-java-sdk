package co.featureflags.server.exterior;

public final class BasicConfig {

    private final String envSecret;
    private final boolean offline;

    public BasicConfig(String envSecret, boolean offline) {
        this.envSecret = envSecret;
        this.offline = offline;
    }

    public BasicConfig(String envSecret) {
        this.envSecret = envSecret;
        this.offline = false;
    }

    public String getEnvSecret() {
        return envSecret;
    }

    public boolean isOffline() {
        return offline;
    }
}
