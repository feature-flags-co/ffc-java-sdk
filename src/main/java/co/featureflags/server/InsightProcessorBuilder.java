package co.featureflags.server;

import co.featureflags.server.exterior.InsightEventSenderFactory;
import co.featureflags.server.exterior.InsightProcessorFactory;

import java.time.Duration;

/**
 * Factory to create {@link co.featureflags.server.exterior.InsightProcessor}
 * <p>
 * The SDK normally buffers analytics events and sends them to feature-flag.co at intervals. If you want
 * to customize this behavior, create a builder with {@link Factory#insightProcessorFactory()}, change its
 * properties with the methods of this class, and pass it to {@link FFCConfig.Builder#insightProcessorFactory(InsightProcessorFactory)}:
 * <pre><code>
 *      InsightProcessorBuilder insightProcessorBuilder = Factory.insightProcessorFactory()
 *                     .capacity(10000)
 *
 *
 *             FFCConfig config = new FFCConfig.Builder()
 *                     .insightProcessorFactory(insightProcessorBuilder)
 *                     .build();
 *
 *             FFCClient client = new FFCClientImp(envSecret, config);
 * </code></pre>
 * <p>
 * Note that this class is in fact only internal use, it's not recommended to customize any behavior in this configuration.
 * We just keep the same design pattern in the SDK
 */

public abstract class InsightProcessorBuilder implements InsightEventSenderFactory, InsightProcessorFactory {

    protected final static String DEFAULT_EVENT_URI = "https://api.feature-flags.co";
    protected final static int DEFAULT_CAPACITY = 10000;
    protected final static int DEFAULT_RETRY_DELAY = 100;
    protected final static int DEFAULT_RETRY_TIMES = 1;
    protected final static Duration DEFAULT_FLUSH_INTERVAL = Duration.ofSeconds(3);


    protected String eventUri;
    protected int capacity;
    protected long retryIntervalInMilliseconds;
    protected int maxRetryTimes;
    protected final long flushInterval = DEFAULT_FLUSH_INTERVAL.toMillis();

    public InsightProcessorBuilder eventUri(String eventUri) {
        this.eventUri = eventUri;
        return this;
    }

    public InsightProcessorBuilder capacity(int capacityOfInbox) {
        this.capacity = capacityOfInbox;
        return this;
    }

    public InsightProcessorBuilder retryInterval(long retryIntervalInMilliseconds) {
        this.retryIntervalInMilliseconds = retryIntervalInMilliseconds;
        return this;
    }

    public InsightProcessorBuilder maxRetryTimes(int maxRetryTimes) {
        this.maxRetryTimes = maxRetryTimes;
        return this;
    }


}
