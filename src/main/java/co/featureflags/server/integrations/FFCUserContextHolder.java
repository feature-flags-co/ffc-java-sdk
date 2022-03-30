package co.featureflags.server.integrations;

import co.featureflags.commons.model.FFCUser;
import com.alibaba.ttl.TransmittableThreadLocal;

public class FFCUserContextHolder {
    private static final ThreadLocal<FFCUser> userThreadLocal = new ThreadLocal<>();
    private static final TransmittableThreadLocal<FFCUser> inheritedUserThreadLocal = new TransmittableThreadLocal<>();

    public static FFCUser getCurrentUser() {
        FFCUser user = inheritedUserThreadLocal.get();
        if (user == null) {
            user = userThreadLocal.get();
        }
        return user;
    }

    public static void remove() {
        userThreadLocal.remove();
        inheritedUserThreadLocal.remove();
    }

    public static void setCurrentUser(FFCUser user, boolean inherit) {
        if (inherit) {
            inheritedUserThreadLocal.set(user);
        } else {
            userThreadLocal.set(user);
        }
    }
}
