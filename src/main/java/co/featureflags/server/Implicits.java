package co.featureflags.server;

import co.featureflags.commons.model.AllFlagStates;
import co.featureflags.commons.model.EvalDetail;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;

abstract class Implicits {
    static final class ComplexAllFlagStates<T> extends AllFlagStates<T> {

        private transient final Consumer<InsightTypes.Event> eventHandler;

        private transient Map<EvalDetail<T>, InsightTypes.Event> complexData;


        ComplexAllFlagStates(boolean success,
                             String message,
                             Map<EvalDetail<T>, InsightTypes.Event> complexData,
                             Consumer<InsightTypes.Event> eventHandler) {
            super(success, message, complexData == null ? null : new ArrayList<>(complexData.keySet()));
            this.complexData = complexData;
            this.eventHandler = eventHandler;
        }

        @Override
        public EvalDetail get(String flagKeyName) {
            EvalDetail ed = super.get(flagKeyName);
            if (ed != null && eventHandler != null && complexData != null) {
                InsightTypes.Event event = complexData.get(ed);
                eventHandler.accept(event);
            }
            return ed;
        }
    }
}
