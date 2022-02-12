package co.featureflags.server.exterior.model;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;

public final class FFCUser {

    private final static Function<FFCUser, String> USERNAME = u -> u.userName;
    private final static Function<FFCUser, String> EMAIL = u -> u.email;
    private final static Function<FFCUser, String> KEY = u -> u.key;
    private final static Function<FFCUser, String> COUNTRY = u -> u.country;

    private final static Map<String, Function<FFCUser, String>> BUILTINS = ImmutableMap.of("Name", USERNAME,
            "KeyId", KEY,
            "Country", COUNTRY,
            "Email", EMAIL);


    private final String userName;
    private final String email;
    private final String key;
    private final String country;
    //TODO property for generic type
    private final Map<String, String> custom;

    private FFCUser(Builder builder) {
        String key = builder.key;
        checkArgument(StringUtils.isNotBlank(key), "Key shouldn't be empty");
        this.key = key;
        this.email = builder.email == null ? "" : builder.email;
        this.userName = builder.userName == null ? "" : builder.userName;
        this.country = builder.country == null ? "" : builder.country;
        ImmutableMap.Builder<String, String> map = ImmutableMap.builder();
        for (Map.Entry<String, String> entry : builder.custom.entrySet()) {
            if (!BUILTINS.containsKey(entry.getKey())) {
                map.put(entry.getKey(), entry.getValue());
            }
        }
        this.custom = map.build();
    }

    public String getUserName() {
        return userName;
    }

    public String getEmail() {
        return email;
    }

    public String getKey() {
        return key;
    }

    public String getCountry() {
        return country;
    }

    public Map<String, String> getCustom() {
        return custom;
    }

    public String getProperty(String property) {
        Function<FFCUser, String> f = BUILTINS.get(key);
        if (f == null) {
            return custom.get(property);
        }
        return f.apply(this);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("userName", userName)
                .add("email", email)
                .add("key", key)
                .add("country", country)
                .add("custom", custom)
                .toString();
    }


    public static class Builder {
        private String userName;

        private String email;

        private String key;

        private String country;

        private final Map<String, String> custom = new HashMap<>();

        public Builder(String key) {
            this.key = key;
        }

        public FFCUser.Builder key(String s) {
            this.key = s;
            return this;
        }

        public FFCUser.Builder email(String s) {
            this.email = s;
            return this;
        }

        public FFCUser.Builder userName(String s) {
            this.userName = s;
            return this;
        }

        public FFCUser.Builder country(String s) {
            this.country = s;
            return this;
        }

        public FFCUser.Builder custom(String key, String value) {
            if (StringUtils.isNotBlank(key) && value != null) {
                custom.put(key, value);
            }
            return this;
        }

        public FFCUser build() {
            return new FFCUser(this);
        }
    }

}
