package co.featureflags.server;

import co.featureflags.server.exterior.BasicConfig;
import co.featureflags.server.exterior.Context;
import co.featureflags.server.exterior.HttpConfig;

final class ContextImp implements Context {
    private final HttpConfig httpConfig;
    private final BasicConfig basicConfig;

    ContextImp(String envSecret, FFCConfig config) {
        this.basicConfig = new BasicConfig(envSecret, config.isOffline());
        this.httpConfig = config.getHttpConfigFactory().createHttpConfig(basicConfig);
    }

    ContextImp(HttpConfig httpConfig, BasicConfig basicConfig) {
        this.httpConfig = httpConfig;
        this.basicConfig = basicConfig;
    }

    @Override
    public BasicConfig basicConfig() {
        return basicConfig;
    }

    @Override
    public HttpConfig http() {
        return httpConfig;
    }
}
