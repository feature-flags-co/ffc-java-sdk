package co.featureflags;

import co.featureflags.model.FFCUser;
import co.featureflags.model.FFCUserProperty;

import java.util.ArrayList;
import java.util.List;

class IntermediateObject {
    static class VariationPayload {
        String featureFlagKeyName;
        String environmentSecret;
        String ffUserName;
        String ffUserEmail;
        String ffUserCountry;
        String ffUserKeyId;
        Pair[] ffUserCustomizedProperties;

        VariationPayload(String featureFlag, String envSecret, FFCUser user) {
            this.featureFlagKeyName = featureFlag;
            this.environmentSecret = envSecret;
            this.ffUserKeyId = user.getKey();
            this.ffUserName = user.getUserName();
            this.ffUserEmail = user.getEmail();
            this.ffUserCountry = user.getCountry();
            List<Pair> pairs = new ArrayList<>();
            for (FFCUserProperty p : user.getCustomProperties()) {
                String na = p.getName();
                String v = user.getProperty(p);
                pairs.add(new Pair(na, v));
            }
            if (!pairs.isEmpty()){
                ffUserCustomizedProperties = new Pair[pairs.size()];
                pairs.toArray(ffUserCustomizedProperties);
            }
        }

    }

    static class VariationOption{
        int localId;
        int displayOrder;
        String variationValue;

        VariationOption(int localId, int displayOrder, String variationValue) {
            this.localId = localId;
            this.displayOrder = displayOrder;
            this.variationValue = variationValue;
        }
    }

    static class Pair {
        String name;
        String value;

        Pair(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }
}
