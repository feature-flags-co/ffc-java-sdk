package co.featureflags.server;

import co.featureflags.server.exterior.DataStorage;
import co.featureflags.server.exterior.DataStoreTypes;
import com.google.common.base.MoreObjects;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public abstract class Status {

    public static final String DATA_STORAGE_INIT_ERROR = "Data Storage init error";
    public static final String DATA_STORAGE_UPDATE_ERROR = "Data Storage update error";
    public static final String REQUEST_INVALID_ERROR = "Request invalid";
    public static final String DATA_INVALID_ERROR = "Received Data invalid";
    public static final String NETWORK_ERROR = "Network error";
    public static final String RUNTIME_ERROR = "Runtime error";
    public static final String UNKNOWN_ERROR = "Unknown error";
    public static final String UNKNOWN_CLOSE_CODE = "Unknown close code";


    public enum StateType {
        INITIALIZING, OK, INTERRUPTED, OFF
    }

    public static class ErrorInfo implements Serializable {
        private final String errorType;
        private final String message;

        private ErrorInfo(String errorType, String message) {
            this.errorType = errorType;
            this.message = message;
        }

        public static ErrorInfo of(String errorType, String message) {
            return new ErrorInfo(errorType, message);
        }

        public static ErrorInfo of(String errorType) {
            return new ErrorInfo(errorType, null);
        }

        public String getErrorType() {
            return errorType;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ErrorInfo errorInfo = (ErrorInfo) o;
            return Objects.equals(errorType, errorInfo.errorType) && Objects.equals(message, errorInfo.message);
        }

        @Override
        public int hashCode() {
            return Objects.hash(errorType, message);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("errorType", errorType).add("message", message).toString();
        }
    }

    public static final class State implements Serializable {
        private final StateType stateType;
        private final Instant stateSince;
        private final ErrorInfo info;

        private State(StateType stateType, Instant stateSince, ErrorInfo info) {
            this.stateType = stateType;
            this.stateSince = stateSince;
            this.info = info;
        }

        public static State initializingState() {
            return new State(StateType.INITIALIZING, Instant.now(), null);
        }

        public static State OKState() {
            return new State(StateType.OK, Instant.now(), null);
        }

        public static State normalOFFState() {
            return new State(StateType.OFF, Instant.now(), null);
        }

        public static State errorOFFState(String errorType, String message) {
            return new State(StateType.OFF, Instant.now(), ErrorInfo.of(errorType, message));
        }

        public static State interruptedState(String errorType, String message) {
            return new State(StateType.INTERRUPTED, Instant.now(), ErrorInfo.of(errorType, message));
        }

        public static State of(StateType stateType, Instant stateSince, String errorType, String message) {
            return new State(stateType, stateSince, ErrorInfo.of(errorType, message));
        }

        public StateType getStateType() {
            return stateType;
        }

        public Instant getStateSince() {
            return stateSince;
        }

        public ErrorInfo getInfo() {
            return info;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).add("stateType", stateType).add("stateSince", stateSince).add("info", info).toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            State state = (State) o;
            return stateType == state.stateType && Objects.equals(stateSince, state.stateSince) && Objects.equals(info, state.info);
        }

        @Override
        public int hashCode() {
            return Objects.hash(stateType, stateSince, info);
        }
    }

    /**
     * Interface that {@link co.featureflags.server.exterior.UpdateProcessor} implementation will use to push data into the SDK.
     * <p>
     * The {@link co.featureflags.server.exterior.UpdateProcessor} interacts with this object, rather than manipulating the {@link DataStorage} directly,
     * so that the SDK can perform any other necessary operations that should perform around data updating.
     * <p>
     * if you overwrite the our default Update Processor,you should integrate{@link DataUpdator} to push data
     * and maintain the processor status in your own code
     */
    public interface DataUpdator {
        boolean init(Map<DataStoreTypes.Category, Map<String, DataStoreTypes.Item>> allData, Long version);

        boolean upsert(DataStoreTypes.Category category, String key, DataStoreTypes.Item item, Long version);

        void updateStatus(StateType newState, ErrorInfo message);

        long getVersion();

        boolean storageInitialized();

    }

    /**
     * The {@link co.featureflags.server.exterior.UpdateProcessor} will push updates into this component. This component
     * then apply necessary transformations, like status management(checking, updating, notifying etc.), failure tracking,
     * before putting updating items into data storage.
     * <p>
     * This component is thread safe and is basic component usd in bootstrapping.
     */
    static final class DataUpdatorImpl implements DataUpdator {

        private final DataStorage storage;
        private volatile State currentState;
        private final Object lockObject = new Object();
        // todo FlagChangeNotifier, StatusNotifier, ErrorAnalyser

        public DataUpdatorImpl(DataStorage storage) {
            this.storage = storage;
            this.currentState = State.initializingState();
        }

        private void handleErrorFromStorage(Exception ex, ErrorInfo errorInfo) {
            Loggers.DATA_STORAGE.warn("Data Storage error: {}, UpdateProcessor will attempt to receive the data", ex.getMessage());
            updateStatus(StateType.INTERRUPTED, errorInfo);
        }

        @Override
        public boolean init(Map<DataStoreTypes.Category, Map<String, DataStoreTypes.Item>> allData, Long version) {
            try {
                storage.init(allData, version);
            } catch (Exception ex) {
                handleErrorFromStorage(ex, ErrorInfo.of(DATA_STORAGE_INIT_ERROR, ex.getMessage()));
                return false;
            }
            //TODO Flag Change Notifying->new thread
            return true;
        }

        @Override
        public boolean upsert(DataStoreTypes.Category category, String key, DataStoreTypes.Item item, Long version) {
            try {
                storage.upsert(category, key, item, version);
            } catch (Exception ex) {
                handleErrorFromStorage(ex, ErrorInfo.of(DATA_STORAGE_UPDATE_ERROR, ex.getMessage()));
                return false;
            }
            //TODO Flag Change Notifying->new thread
            return true;
        }

        @Override
        public void updateStatus(StateType newState, ErrorInfo message) {
            if (newState == null) {
                return;
            }
            synchronized (lockObject) {
                StateType oldOne = currentState.getStateType();
                // interruped state is only meaningful after initialization
                if (newState == StateType.INTERRUPTED && oldOne == StateType.INITIALIZING) {
                    newState = StateType.INITIALIZING;
                }

                if (newState != oldOne || message != null) {
                    Instant stateSince = newState == oldOne ? currentState.getStateSince() : Instant.now();
                    currentState = new State(newState, stateSince, message);
                    lockObject.notifyAll();
                }
            }
            //TODO ErrorAnalyser ->new thread
            //TODO status changes notifying->new thread

        }

        @Override
        public long getVersion() {
            return storage.getVersion();
        }

        @Override
        public boolean storageInitialized() {
            return storage.isInitialized();
        }

        // blocking util you get the desired state, time out reaches or thread is interrupted
        boolean waitFor(StateType state, Duration timeout) throws InterruptedException {
            Instant deadline = Instant.now().plus(timeout);
            synchronized (lockObject) {
                while (true) {
                    StateType curr = currentState.getStateType();
                    if (curr == state) {
                        return true;
                    }
                    if (curr == StateType.OFF) {
                        return false;
                    }
                    if (timeout.isZero() || timeout.isNegative()) {
                        lockObject.wait();
                    } else {
                        // block the consumer thread util getting desired state
                        // or quitting in timeout
                        Instant now = Instant.now();
                        if (now.isAfter(deadline)) {
                            return false;
                        }
                        Duration rest = Duration.between(now, deadline);
                        lockObject.wait(rest.toMillis(), 1);
                    }
                }
            }
        }

        State getCurrentState() {
            synchronized (lockObject) {
                return currentState;
            }
        }

    }

    public interface DataUpdateStatusProvider {

        State getState();

        boolean waitFor(StateType state, Duration timeout) throws InterruptedException;

        boolean waitForOKState(Duration timeout) throws InterruptedException;

    }

    static final class DataUpdateStatusProviderImpl implements DataUpdateStatusProvider {

        private final DataUpdatorImpl dataUpdator;

        public DataUpdateStatusProviderImpl(DataUpdatorImpl dataUpdator) {
            this.dataUpdator = dataUpdator;
        }

        @Override
        public State getState() {
            return dataUpdator.getCurrentState();
        }

        @Override
        public boolean waitFor(StateType state, Duration timeout) throws InterruptedException {
            return dataUpdator.waitFor(state, timeout);
        }

        @Override
        public boolean waitForOKState(Duration timeout) throws InterruptedException {
            return waitFor(StateType.OK, timeout);
        }
    }


}
