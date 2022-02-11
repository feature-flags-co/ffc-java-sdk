package co.featureflags.server.exterior;

public interface HttpConfigFactory {
    HttpConfig createHttpConfig(BasicConfig config);
}
