package co.featureflags.server;

import co.featureflags.server.exterior.InsightEventSenderFactory;
import co.featureflags.server.exterior.InsightProcessorFactory;

import java.time.Duration;

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
