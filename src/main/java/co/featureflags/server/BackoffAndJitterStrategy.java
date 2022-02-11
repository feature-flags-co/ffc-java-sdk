package co.featureflags.server;

import java.time.Duration;
import java.time.Instant;

class BackoffAndJitterStrategy {

    private final Duration firstRetryDelay;
    private final Duration maxRetryDelay;
    private final Duration resetInterval;
    private final Double jitterRatio;
    private Instant latestGoodRun;
    private Integer retryCount;


    BackoffAndJitterStrategy(Duration firstRetryDelay,
                             Duration maxRetryDelay,
                             Duration resetInterval,
                             Double jitterRatio) {
        this.firstRetryDelay = firstRetryDelay == null ? Duration.ofSeconds(1) : firstRetryDelay;
        this.maxRetryDelay = maxRetryDelay == null ? Duration.ofSeconds(60) : maxRetryDelay;
        this.resetInterval = resetInterval == null ? Duration.ofSeconds(60) : resetInterval;
        this.jitterRatio = (jitterRatio == null || jitterRatio > 1D || jitterRatio < 0D) ? 0.5D : jitterRatio;
        this.latestGoodRun = Instant.ofEpochSecond(0);
        this.retryCount = 0;
    }

    BackoffAndJitterStrategy(Duration firstRetryDelay) {
        this(firstRetryDelay, null, null, null);
    }

    void setGoodRunAtNow() {
        latestGoodRun = Instant.now();
    }

    private Double countJitterTime(Double delay) {
        return delay * jitterRatio * Math.random();
    }

    private Double countBackoffTime() {
        Double delay = new Double(firstRetryDelay.toMillis()) * Math.pow(2D, new Double(retryCount));
        Double maxValue = new Double(maxRetryDelay.toMillis());
        return delay <= maxValue ? delay : maxValue;
    }

    Duration nextDelay(boolean forceToUseMaxRetryDelay) {
        Duration duration;
        Instant now = Instant.now();
        Duration interval = Duration.between(latestGoodRun, now);
        if (resetInterval.minus(interval).isNegative()) {
            retryCount = 0;
        }
        if (forceToUseMaxRetryDelay) {
            retryCount = 0;
            duration = maxRetryDelay;
        } else {
            Double backoffTime = countBackoffTime();
            Long delayInMillis = Math.round(countJitterTime(backoffTime) + backoffTime / 2);
            duration = Duration.ofMillis(delayInMillis);
        }
        retryCount++;
        return duration;
    }


}
