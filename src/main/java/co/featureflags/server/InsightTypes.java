package co.featureflags.server;

import co.featureflags.commons.model.FFCUser;
import co.featureflags.server.exterior.InsightEventSender;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.JsonAdapter;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import static co.featureflags.server.Evaluator.NO_EVAL_RES;

public abstract class InsightTypes {

    public static abstract class Event {
        protected final FFCUser user;

        Event(FFCUser user) {
            this.user = user;
        }

        public FFCUser getUser() {
            return user;
        }

        public abstract boolean isSendEvent();

        public abstract Event add(Object element);
    }

    final static class NullEvent extends Event {
        static final NullEvent INSTANCE = new NullEvent();
        private NullEvent() {
            super(null);
        }

        @Override
        public boolean isSendEvent() {
            return false;
        }

        @Override
        public Event add(Object element) {
            return null;
        }
    }

    @JsonAdapter(FlagEventSerializer.class)
    final static class FlagEvent extends Event {
        private final List<FlagEventVariation> userVariations = new ArrayList<>();

        private FlagEvent(FFCUser user) {
            super(user);
        }

        static FlagEvent of(FFCUser user) {
            return new FlagEvent(user);
        }

        @Override
        public Event add(Object element) {
            FlagEventVariation variation = (FlagEventVariation) element;
            if (variation != null && !variation.getVariation().getIndex().equals(NO_EVAL_RES)) {
                userVariations.add(variation);
            }
            return this;
        }

        @Override
        public boolean isSendEvent() {
            return user != null && !userVariations.isEmpty();
        }
    }

    @JsonAdapter(MetricEventSerializer.class)
    final static class MetricEvent extends Event {
        private final List<Metric> metrics = new ArrayList<>();

        MetricEvent(FFCUser user) {
            super(user);
        }

        static MetricEvent of(FFCUser user) {
            return new MetricEvent(user);
        }

        @Override
        public boolean isSendEvent() {
            return user != null && !metrics.isEmpty();
        }

        @Override
        public Event add(Object element) {
            Metric metric = (Metric) element;
            if (metric != null) {
                metrics.add(metric);
            }
            return this;
        }
    }

    static final class FlagEventVariation {
        private final String featureFlagKeyName;
        private final long timestamp;
        private final Evaluator.EvalResult variation;

        FlagEventVariation(String featureFlagKeyName, long timestamp, Evaluator.EvalResult variation) {
            this.featureFlagKeyName = featureFlagKeyName;
            this.timestamp = timestamp;
            this.variation = variation;
        }

        static FlagEventVariation of(String featureFlagKeyName, Evaluator.EvalResult variation) {
            return new FlagEventVariation(featureFlagKeyName, Instant.now().toEpochMilli(), variation);
        }

        public String getFeatureFlagKeyName() {
            return featureFlagKeyName;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public Evaluator.EvalResult getVariation() {
            return variation;
        }
    }

    static final class Metric {
        private final String route = "index/metric";
        private final String type = "CustomEvent";
        private final String eventName;
        private final Double numericValue;
        private final String appType = "javaserverside";

        Metric(String eventName, Double numericValue) {
            this.eventName = eventName;
            this.numericValue = numericValue;
        }

        static Metric of(String eventName, Double numericValue) {
            return new Metric(eventName, numericValue == null ? 1.0D : numericValue);
        }

        public String getEventName() {
            return eventName;
        }

        public Double getNumericValue() {
            return numericValue;
        }

        public String getRoute() {
            return route;
        }

        public String getType() {
            return type;
        }

        public String getAppType() {
            return appType;
        }
    }

    final static class FlagEventSerializer implements JsonSerializer<FlagEvent> {

        @Override
        public JsonElement serialize(FlagEvent flagEvent, Type type, JsonSerializationContext jsonSerializationContext) {
            FFCUser user = flagEvent.getUser();
            JsonObject json = serializeUser(user);
            JsonArray array1 = new JsonArray();
            for (FlagEventVariation variation : flagEvent.userVariations) {
                JsonObject var = new JsonObject();
                var.addProperty("featureFlagKeyName", variation.getFeatureFlagKeyName());
                var.addProperty("sendToExperiment", variation.getVariation().isSendToExperiment());
                var.addProperty("timestamp", Instant.now().toEpochMilli());
                JsonObject v = new JsonObject();
                v.addProperty("localId", variation.getVariation().getIndex());
                v.addProperty("variationValue", variation.getVariation().getValue());
                v.addProperty("reason", variation.getVariation().getReason());
                var.add("variation", v);
                array1.add(var);
            }
            json.add("userVariations", array1);
            return json;
        }
    }

    final static class MetricEventSerializer implements JsonSerializer<MetricEvent> {
        @Override
        public JsonElement serialize(MetricEvent metricEvent, Type type, JsonSerializationContext jsonSerializationContext) {
            FFCUser user = metricEvent.getUser();
            JsonObject json = serializeUser(user);
            JsonArray array1 = new JsonArray();
            for (Metric metric : metricEvent.metrics) {
                JsonObject var = new JsonObject();
                var.addProperty("route", metric.getRoute());
                var.addProperty("type", metric.getType());
                var.addProperty("eventName", metric.getEventName());
                var.addProperty("numericValue", metric.getNumericValue());
                var.addProperty("appType", metric.getAppType());
                array1.add(var);
            }
            json.add("metrics", array1);
            return json;
        }
    }

    private static JsonObject serializeUser(FFCUser user) {
        JsonObject json = new JsonObject();
        JsonObject json1 = new JsonObject();
        json1.addProperty("userName", user.getUserName());
        json1.addProperty("email", user.getEmail());
        json1.addProperty("country", user.getCountry());
        json1.addProperty("keyId", user.getKey());
        JsonArray array = new JsonArray();
        for (Map.Entry<String, String> keyItem : user.getCustom().entrySet()) {
            JsonObject p = new JsonObject();
            p.addProperty("name", keyItem.getKey());
            p.addProperty("value", keyItem.getValue());
            array.add(p);
        }
        json1.add("customizedProperties", array);
        json.add("user", json1);
        return json;
    }

    enum InsightMessageType {
        FLAGS, FLUSH, SHUTDOWN, METRICS,
    }

    static final class InsightMessage {
        private final InsightMessageType type;
        private final Event event;
        private final Semaphore waitLock;

        // waitLock is initialized only when you need to wait until the message is completely handled
        // Ex, shutdown, in this case, we should to wait until all events are sent to server
        InsightMessage(InsightMessageType type, Event event, boolean awaitTermination) {
            this.type = type;
            this.event = event;
            // permit = 0, so wait until a permit releases
            this.waitLock = awaitTermination ? new Semaphore(0) : null;
        }

        public void completed() {
            if (waitLock != null) {
                waitLock.release();
            }
        }

        public void waitForComplete() {
            if (waitLock == null) {
                return;
            }
            while (true) {
                try {
                    waitLock.acquire();
                    return;
                } catch (InterruptedException ignore) {
                }
            }

        }

        public InsightMessageType getType() {
            return type;
        }

        public Event getEvent() {
            return event;
        }
    }

    static final class InsightConfig {

        private static final String EVENT_PATH = "/api/public/track";

        final InsightEventSender sender;
        final String eventUrl;
        final long flushInterval;
        final int capacity;

        InsightConfig(InsightEventSender sender, String baseUri, long flushInterval, int capacity) {
            this.sender = sender;
            this.eventUrl = StringUtils.stripEnd(baseUri, "/").concat(EVENT_PATH);
            this.flushInterval = flushInterval;
            this.capacity = capacity;
        }

        public InsightEventSender getSender() {
            return sender;
        }

        public String getEventUrl() {
            return eventUrl;
        }

        public long getFlushInterval() {
            return flushInterval;
        }

        public int getCapacity() {
            return capacity;
        }
    }


}
