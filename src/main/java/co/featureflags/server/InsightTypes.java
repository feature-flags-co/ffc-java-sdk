package co.featureflags.server;

import co.featureflags.server.exterior.InsightEventSender;
import co.featureflags.server.exterior.model.FFCUser;
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
            if (!variation.getVariation().getIndex().equals(NO_EVAL_RES)) {
                userVariations.add(variation);
            }
            return this;
        }

        @Override
        public boolean isSendEvent() {
            return user != null && !userVariations.isEmpty();
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

    final static class FlagEventSerializer implements JsonSerializer<FlagEvent> {
        @Override
        public JsonElement serialize(FlagEvent flagEvent, Type type, JsonSerializationContext jsonSerializationContext) {
            FFCUser user = flagEvent.getUser();
            JsonObject json = new JsonObject();
            json.addProperty("userName", user.getUserName());
            json.addProperty("email", user.getEmail());
            json.addProperty("country", user.getCountry());
            json.addProperty("userKeyId", user.getKey());
            JsonArray array = new JsonArray();
            for (Map.Entry<String, String> keyItem : user.getCustom().entrySet()) {
                JsonObject p = new JsonObject();
                p.addProperty("name", keyItem.getKey());
                p.addProperty("value", keyItem.getValue());
                array.add(p);
            }
            json.add("userCustomizedProperties", array);
            JsonArray array1 = new JsonArray();
            for (FlagEventVariation variation : flagEvent.userVariations) {
                JsonObject var = new JsonObject();
                var.addProperty("featureFlagKeyName", variation.getFeatureFlagKeyName());
                var.addProperty("sendToExperiment", variation.getVariation().isSendToExperiment());
                var.addProperty("timestamp", variation.getTimestamp());
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

    enum InsightMessageType {
        EVENT, FLUSH, SHUTDOWN
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

        public void waitForComplete(Duration timeout) {
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

        private static final String EVENT_PATH = "/api/public/analytics/track/feature-flags";

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
