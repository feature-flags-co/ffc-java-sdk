package co.featureflags.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class FFCUserProperty {

    public static final FFCUserProperty KEY = new FFCUserProperty("key", u -> u.getKey());

    public static final FFCUserProperty EMAIL = new FFCUserProperty("email", u -> u.getEmail());

    public static final FFCUserProperty USERNAME = new FFCUserProperty("userName", u -> u.getUserName());

    public static final FFCUserProperty COUNTRY = new FFCUserProperty("country", u -> u.getCountry());

    static final Map<String, FFCUserProperty> BUILTINS;

    static {
        Map<String, FFCUserProperty> map = new HashMap<>();
        for (FFCUserProperty p : new FFCUserProperty[]{KEY, EMAIL, USERNAME, COUNTRY}) {
            map.put(p.getName(), p);
        }
        BUILTINS = Collections.unmodifiableMap(map);
    }

    private final String name;

    final Function<FFCUser, String> builtInGetter;


    private FFCUserProperty(String name, Function<FFCUser, String> builtInGetter) {
        this.name = name;
        this.builtInGetter = builtInGetter;
    }

    public String getName() {
        return name;
    }

    public static FFCUserProperty forName(String name) {
        FFCUserProperty p = BUILTINS.get(name);
        return p != null ? p : new FFCUserProperty(name, null);
    }

    public boolean isBuiltIn() {
        return builtInGetter != null;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof FFCUserProperty) {
            FFCUserProperty o = (FFCUserProperty) other;
            if (isBuiltIn() || o.isBuiltIn()) {
                return this == o;
            }
            return name.equals(o.name);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return isBuiltIn() ? super.hashCode() : name.hashCode();
    }
}
