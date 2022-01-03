package co.featureflags;

public final class FFCConfig {
    static final String DEFAULT_BASE_URI = "https://api.feature-flags.co";
    static final String DEFAULT_APP_TYPE = "Java";
    static final int DEFAULT_TIME_OUT = 300;
    static final String VARIATION_PATH = "/Variation/GetMultiOptionVariation";

    static final FFCConfig DEFAULT = new FFCConfig.Builder().build();

    final String baseUrl;
    final String appType;
    final int timeout;

    protected FFCConfig(FFCConfig.Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.appType = builder.appType;
        this.timeout = builder.timeout;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getAppType() {
        return appType;
    }

    public int getTimeout() {
        return timeout;
    }

    public static class Builder {
        private String baseUrl = DEFAULT_BASE_URI;
        private String appType = DEFAULT_APP_TYPE;
        private int timeout = DEFAULT_TIME_OUT;

        public Builder() {
        }

        public FFCConfig.Builder baseUrl(String s) {
            this.baseUrl = s;
            return this;
        }

        public FFCConfig.Builder appType(String s) {
            this.appType = s;
            return this;
        }

        public FFCConfig.Builder timeoutInSeconds(int i) {
            this.timeout = i;
            return this;
        }

        public FFCConfig build() {
            return new FFCConfig(this);
        }

    }
}
