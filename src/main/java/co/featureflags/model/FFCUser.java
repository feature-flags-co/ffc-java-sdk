package co.featureflags.model;

import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

public class FFCUser {
    private final String userName;

    private final String email;

    private final String key;

    private final String country;

    private final Map<FFCUserProperty, String> custom;

    public FFCUser(String key) {
        checkArgument(StringUtils.isNotEmpty(key) && StringUtils.isNotBlank(key), "Key shouldn't be empty");
        this.key = key;
        this.userName = this.email = this.country = null;
        this.custom = null;
    }

    protected FFCUser(FFCUser.Builder builder) {
        String key = builder.key;
        checkArgument(StringUtils.isNotEmpty(key) && StringUtils.isNotBlank(key), "Key shouldn't be empty");
        this.key = key;
        this.email = builder.email == null ? "" : builder.email;
        this.userName = builder.userName == null ? "" : builder.userName;
        this.country = builder.country == null ? "" : builder.country;
        this.custom = builder.custom == null ? null : Collections.unmodifiableMap(builder.custom);
    }

    public Iterable<FFCUserProperty> getCustomProperties() {
        return custom == null ? Collections.<FFCUserProperty>emptyList() : custom.keySet();
    }

    public String getProperty(FFCUserProperty property) {
        if (property.isBuiltIn()) {
            return property.builtInGetter.apply(this);
        } else {
            return custom == null ? null : custom.get(property);
        }
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FFCUser)) return false;

        FFCUser ffcUser = (FFCUser) o;

        if (!userName.equals(ffcUser.userName)) return false;
        if (!email.equals(ffcUser.email)) return false;
        if (!key.equals(ffcUser.key)) return false;
        if (!country.equals(ffcUser.country)) return false;
        return custom.equals(ffcUser.custom);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, userName, email, country, custom);
    }

    public static class Builder {
        private String userName;

        private String email;

        private String key;

        private String country;

        private Map<FFCUserProperty, String> custom;

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
            if (StringUtils.isNotEmpty(key) && StringUtils.isNotBlank(key)) {
                return customInternal(FFCUserProperty.forName(key), value);
            }
            return this;
        }

        private FFCUser.Builder customInternal(FFCUserProperty p, String v) {
            if (custom == null) {
                custom = new HashMap<>();
            }
            custom.put(p, v);
            return this;
        }

        public FFCUser build() {
            return new FFCUser(this);
        }
    }
}
