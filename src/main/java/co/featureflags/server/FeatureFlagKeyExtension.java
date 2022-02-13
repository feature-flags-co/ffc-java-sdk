package co.featureflags.server;

import org.apache.commons.codec.binary.Base64;

import java.nio.charset.StandardCharsets;

abstract class FeatureFlagKeyExtension {
    private FeatureFlagKeyExtension() {
        super();
    }

    static String buildFeatureFlagId(String featureFlagKeyName,
                                     String envId,
                                     String accountId,
                                     String projectId) {
        return String.format("FF__%s__%s__%s__%s", accountId, projectId, envId, featureFlagKeyName);
    }

    static String unpackFeatureFlagId(String featureFlagId, int position) {
        if (featureFlagId == null || position > 4 || position < 0)
            return null;
        return featureFlagId.split("__")[position];
    }

    static final class FeatureFlagIdByEnvSecret {
        private final String featureFlagId;
        private final String envId;
        private final String accountId;
        private final String projectId;

        private FeatureFlagIdByEnvSecret(String featureFlagId,
                                         String envId,
                                         String accountId,
                                         String projectId) {
            this.featureFlagId = featureFlagId;
            this.envId = envId;
            this.accountId = accountId;
            this.projectId = projectId;
        }

        static FeatureFlagIdByEnvSecret of(String envSecret, String featureFlagKeyName) {
            byte[] keyOriginTextByte = Base64.decodeBase64(envSecret);
            String[] keyOriginText = new String(keyOriginTextByte, StandardCharsets.UTF_8).split("__");
            String accountId = keyOriginText[1];
            String projectId = keyOriginText[2];
            String envId = keyOriginText[3];
            String featureFlagId = buildFeatureFlagId(featureFlagKeyName, envId, accountId, projectId);
            return new FeatureFlagIdByEnvSecret(featureFlagId, envId, accountId, projectId);
        }

        FeatureFlagIdByEnvSecret copyForNewFlag(String featureFlagId) {
            return new FeatureFlagIdByEnvSecret(featureFlagId, this.envId, this.accountId, this.projectId);
        }

        public String getFeatureFlagId() {
            return featureFlagId;
        }

        public String getEnvId() {
            return envId;
        }

        public String getAccountId() {
            return accountId;
        }

        public String getProjectId() {
            return projectId;
        }
    }

}
