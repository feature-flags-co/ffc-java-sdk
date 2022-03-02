package co.featureflags.server;

import co.featureflags.server.exterior.DataStorage;
import co.featureflags.server.exterior.DataStoreTypes;
import com.google.common.base.MoreObjects;

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

    /**
     * possible values for {@link co.featureflags.server.exterior.UpdateProcessor}
     */
    public enum StateType {
        /**
         * The initial state of the data source when the SDK is being initialized.
         * <p>
         * If it encounters an error that requires it to retry initialization, the state will remain at
         * {@link #INITIALIZING} until it either succeeds and becomes {@link #OK}, or permanently fails and
         * becomes {@link #OFF}.
         */
        INITIALIZING,
        /**
         * Indicates that the update processing is currently operational and has not had any problems since the
         * last time it received data.
         * <p>
         * In streaming mode, this means that there is currently an open stream connection and that at least
         * one initial message has been received on the stream.
         */
        OK,
        /**
         * Indicates that the update processing encountered an error that it will attempt to recover from.
         * <p>
         * In streaming mode, this means that the stream connection failed, or had to be dropped due to some
         * other error, and will be retried after a backoff delay.
         */
        INTERRUPTED,
        /**
         * Indicates that the update processing has been permanently shut down.
         * <p>
         * This could be because it encountered an unrecoverable error or because the SDK client was
         * explicitly shut down.
         */
        OFF
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
     * and maintain the processor status in your own code, but note that the implementation of this interface is not public
     */
    public interface DataUpdator {
        /**
         * Overwrites the storage with a set of items for each collection, if the new version > the old one
         * <p>
         * If the underlying data store throws an error during this operation, the SDK will catch it, log it,
         * and set the data source state to {@link StateType#INTERRUPTED}.It will not rethrow the error to other level
         * but will simply return {@code false} to indicate that the operation failed.
         *
         * @param allData map of {@link co.featureflags.server.exterior.DataStoreTypes.Category} and their data set {@link co.featureflags.server.exterior.DataStoreTypes.Item}
         * @param version the version of dataset, Ordinarily it's a timestamp.
         * @return true if the update succeeded
         */
        boolean init(Map<DataStoreTypes.Category, Map<String, DataStoreTypes.Item>> allData, Long version);

        /**
         * Updates or inserts an item in the specified collection. For updates, the object will only be
         * updated if the existing version is less than the new version; for inserts, if the version > the existing one, it will replace
         * the existing one.
         * <p>
         * If the underlying data store throws an error during this operation, the SDK will catch it, log it,
         * and set the data source state to {@link StateType#INTERRUPTED}.It will not rethrow the error to other level
         * but will simply return {@code false} to indicate that the operation failed.
         *
         * @param category specifies which collection to use
         * @param key      the unique key of the item in the collection
         * @param item     the item to insert or update
         * @param version  the version of item
         * @return true if success
         */
        boolean upsert(DataStoreTypes.Category category, String key, DataStoreTypes.Item item, Long version);

        /**
         * Informs the SDK of a change in the {@link co.featureflags.server.exterior.UpdateProcessor} status.
         * <p>
         * {@link co.featureflags.server.exterior.UpdateProcessor} implementations should use this method
         * if they have any concept of being in a valid state, a temporarily disconnected state, or a permanently stopped state.
         * <p>
         * If {@code newState} is different from the previous state, and/or {@code newError} is non-null, the
         * SDK will start returning the new status (adding a timestamp for the change) from
         * {@link DataUpdateStatusProvider#getState()}, and will trigger status change events to any
         * registered listeners.
         * <p>
         * A special case is that if {@code newState} is {@link StateType#INTERRUPTED},
         * but the previous state was {@link StateType#INITIALIZING}, the state will remain at {@link StateType#INITIALIZING}
         * because {@link StateType#INTERRUPTED} is only meaningful after a successful startup.
         *
         * @param newState the data storage state
         * @param message  the data source state
         */
        void updateStatus(StateType newState, ErrorInfo message);

        /**
         * return the latest version of {@link DataStorage}
         *
         * @return a long value
         */
        long getVersion();

        /**
         * return true if the {@link DataStorage} is well initialized
         *
         * @return true if the {@link DataStorage} is well initialized
         */
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
            Duration timeout1 = timeout == null ? Duration.ZERO : timeout;
            Instant deadline = Instant.now().plus(timeout1);
            synchronized (lockObject) {
                while (true) {
                    StateType curr = currentState.getStateType();
                    if (curr == state) {
                        return true;
                    }
                    if (curr == StateType.OFF) {
                        return false;
                    }
                    if (timeout1.isZero() || timeout1.isNegative()) {
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

    /**
     * An interface to query the status of a {@link co.featureflags.server.exterior.UpdateProcessor}
     * With the build-in implementation, this might be useful if you want to use SDK without waiting for it to initialize
     */
    public interface DataUpdateStatusProvider {

        /**
         * Returns the current status of the {@link co.featureflags.server.exterior.UpdateProcessor}
         * <p>
         * All of the {@link co.featureflags.server.exterior.UpdateProcessor} implementations are guaranteed to update this status
         * whenever they successfully initialize, encounter an error, or recover after an error.
         * <p>
         * For a custom implementation, it is the responsibility of the data source to report its status via {@link DataUpdator};
         * if it does not do so, the status will always be reported as
         * {@link StateType#INITIALIZING}.
         *
         * @return the latest status; will never be null
         */
        State getState();

        /**
         * A method for waiting for a desired connection state after bootstrapping
         * <p>
         * If the current state is already {@code desiredState} when this method is called, it immediately returns.
         * Otherwise, it blocks until 1. the state has become {@code desiredState}, 2. the state has become
         * {@link StateType#OFF} , 3. the specified timeout elapses, or 4. the current thread is deliberately interrupted with {@link Thread#interrupt()}.
         * <p>
         * A scenario in which this might be useful is if you want to use SDK without waiting
         * for it to initialize, and then wait for initialization at a later time or on a different point:
         * <pre><code>
         *     FFCConfig config = new FFCConfig.Builder()
         *         .startWait(Duration.ZERO)
         *         .build();
         *     FFCClient client = new FFCClient(sdkKey, config);
         *
         *     // later, when you want to wait for initialization to finish:
         *     boolean inited = client.getDataUpdateStatusProvider().waitFor(StateType.OK, Duration.ofSeconds(15))
         *     if (!inited) {
         *         // do whatever is appropriate if initialization has timed out
         *     }
         * </code></pre>
         *
         * @param state   the desired connection state (normally this would be {@link StateType#OK})
         * @param timeout the maximum amount of time to wait-- or {@link Duration#ZERO} to block indefinitely
         *                (unless the thread is explicitly interrupted)
         * @return true if the connection is now in the desired state; false if it timed out, or if the state
         * changed to 2 and that was not the desired state
         * @throws InterruptedException if {@link Thread#interrupt()} was called on this thread while blocked
         */
        boolean waitFor(StateType state, Duration timeout) throws InterruptedException;

        /**
         * alias of {@link #waitFor(StateType, Duration)} in {@link StateType#OK}
         *
         * @param timeout the maximum amount of time to wait-- or {@link Duration#ZERO} to block indefinitely
         *                (unless the thread is explicitly interrupted)
         * @return true if the connection is now in {@link StateType#OK}; false if it timed out, or if the state
         * changed to {@link StateType#OFF} and that was not the desired state
         * @throws InterruptedException
         */
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
