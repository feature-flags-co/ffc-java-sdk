package co.featureflags.server.exterior;

/**
 * Context is used to create components, context information provided by the {@link co.featureflags.server.FFCClientImp}
 * This is passed as parameter to component factories. Component factories do not receive the entire {@link co.featureflags.server.FFCConfig}
 * because it contains only factory implementations.
 * <p>
 * Note that the actual implementation class may contain other properties that are only relevant to the built-in
 * SDK components and are therefore not part of the public interface; this allows the SDK to add its own
 * context information as needed without disturbing the public API.
 */
public interface Context {
    /**
     * the basic config, ex envSecret, offline, of SDK
     * @return basic configuration
     */
    BasicConfig basicConfig();

    /**
     * The networking properties that apply to all components.
     * @return http configuration
     */
    HttpConfig http();

}
