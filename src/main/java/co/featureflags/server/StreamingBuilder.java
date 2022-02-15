package co.featureflags.server;

import co.featureflags.server.exterior.UpdateProcessorFactory;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;

public abstract class StreamingBuilder implements UpdateProcessorFactory {
    protected static final String DEFAULT_STREAMING_URI = "wss://api.feature-flags.co";
    protected static final Duration DEFAULT_FIRST_RETRY_DURATION = Duration.ofSeconds(1);
    protected String streamingURI;
    protected Duration firstRetryDelay;
    protected Integer maxRetryTimes = 0;

    public StreamingBuilder newStreamingURI(String uri) {
        this.streamingURI = StringUtils.isBlank(uri) ? DEFAULT_STREAMING_URI : uri;
        return this;
    }

    public StreamingBuilder firstRetryDelay(Duration duration) {
        this.firstRetryDelay =
                (duration == null || duration.minusSeconds(1).isNegative()) ? DEFAULT_FIRST_RETRY_DURATION : duration;
        return this;
    }

    public StreamingBuilder maxRetryTimes(Integer maxRetryTimes) {
        this.maxRetryTimes = maxRetryTimes;
        return this;
    }
}
