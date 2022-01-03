package co.featureflags;

import co.featureflags.http.DefaultHttpRequestor;
import co.featureflags.model.FFCUser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import static com.google.common.base.Preconditions.checkNotNull;

public class FFCClientImp implements FFCClient {

    private final FFCUser anonymous = new FFCUser.Builder("anonymous")
            .userName("anonymous")
            .build();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private String envSecret;

    private FFCConfig config;

    public FFCClientImp(String envSecret, FFCConfig config) {
        this.envSecret = checkNotNull(envSecret, "envSecret must not be null");
        this.config = checkNotNull(config, "config must not be null");
    }

    public FFCClientImp(String envSecret) {
        this(envSecret, FFCConfig.DEFAULT);
    }

    @Override
    public void initialize(String envSecret, FFCConfig config) {
        if (envSecret != null) {
            this.envSecret = envSecret;
        }
        if (config != null) {
            this.config = config;
        }
    }

    @Override
    public String variation(String featureFlagKey, FFCUser user, String defaultValue) {
        if (featureFlagKey == null) {
            return defaultValue;
        }
        if (user == null) {
            user = anonymous;
        }
        IntermediateObject.VariationPayload payload = new IntermediateObject.VariationPayload(featureFlagKey, this.envSecret, user);
        String json = gson.toJson(payload);
        DefaultHttpRequestor requestor = new DefaultHttpRequestor(this.config);
        try {
            String jsonResponse = requestor.jsonPostDate(FFCConfig.VARIATION_PATH, json);
            IntermediateObject.VariationOption res = gson.fromJson(jsonResponse, IntermediateObject.VariationOption.class);
            return res.variationValue;
        } catch (Exception e) {
            e.printStackTrace();
            return defaultValue;
        }
    }


}
