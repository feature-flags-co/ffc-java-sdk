package co.featureflags.server;

import co.featureflags.server.exterior.UpdateProcessorFactory;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;

/**
 * Factory to create a {@link Streaming} implementation
 * By default, the SDK uses a streaming connection to receive feature flag data. If you want to customize the behavior of the connection,
 * create a builder with {@link Factory#streamingBuilder()}, change its properties with the methods of this class,
 * and pass it to {@link FFCConfig.Builder#updateProcessorFactory(UpdateProcessorFactory)}:
 * <pre><code>
 *      StreamingBuilder streamingBuilder = Factory.streamingBuilder()
 *           .firstRetryDelay(Duration.ofSeconds(1));
 *       FFCConfig config = new FFCConfig.Builder()
 *           .updateProcessorFactory(streamingBuilder)
 *           .build();
 *       FFCClient client = new FFCClientImp(envSecret, config);
 * </code></pre>
 */
public abstract class StreamingBuilder implements UpdateProcessorFactory {
    protected static final String DEFAULT_STREAMING_URI = "wss://api.featureflag.co";
    protected static final Duration DEFAULT_FIRST_RETRY_DURATION = Duration.ofSeconds(1);
    protected String streamingURI;
    protected Duration firstRetryDelay;
    protected Integer maxRetryTimes = 0;

    /**
     * internal test purpose only
     *
     * @param uri streaming base uri
     * @return the builder
     */
    public StreamingBuilder newStreamingURI(String uri) {
        this.streamingURI = StringUtils.isBlank(uri) ? DEFAULT_STREAMING_URI : uri;
        return this;
    }

    /**
     * Sets the initial reconnect delay for the streaming connection.
     * <p>
     * The streaming service uses a backoff algorithm (with jitter) every time the connection needs
     * to be reestablished. The delay for the first reconnection will start near this value, and then
     * increase exponentially for any subsequent connection failures.
     *
     * @param duration the reconnect time base value; null to use the default(1s)
     * @return the builder
     */
    public StreamingBuilder firstRetryDelay(Duration duration) {
        this.firstRetryDelay =
                (duration == null || duration.minusSeconds(1).isNegative()) ? DEFAULT_FIRST_RETRY_DURATION : duration;
        return this;
    }

    /**
     * Sets the max retry times for the streaming failures.
     *
     * @param maxRetryTimes an int value if less than or equals to 0, use the default
     * @return the builder
     */
    public StreamingBuilder maxRetryTimes(Integer maxRetryTimes) {
        this.maxRetryTimes = maxRetryTimes;
        return this;
    }
}
