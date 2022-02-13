package co.featureflags.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class Loggers {
    static final String BASE_LOGGER_NAME = FFCClientImp.class.getName();
    static final Logger CLIENT = LoggerFactory.getLogger(BASE_LOGGER_NAME);
    private static final String DATA_UPDATE_PROCESSOR_LOGGER_NAME = BASE_LOGGER_NAME + ".UpdateProcessor";
    static final Logger UPDATE_PROCESSOR = LoggerFactory.getLogger(DATA_UPDATE_PROCESSOR_LOGGER_NAME);
    private static final String DATA_STORAGE_LOGGER_NAME = BASE_LOGGER_NAME + ".DataStorage";
    static final Logger DATA_STORAGE = LoggerFactory.getLogger(DATA_STORAGE_LOGGER_NAME);
    private static final String EVALUATION_LOGGER_NAME = BASE_LOGGER_NAME + ".Evaluation";
    static final Logger EVALUATION = LoggerFactory.getLogger(EVALUATION_LOGGER_NAME);
    private static final String EVENTS_LOGGER_NAME = BASE_LOGGER_NAME + ".Events";
    static final Logger EVENTS = LoggerFactory.getLogger(EVENTS_LOGGER_NAME);

    Loggers() {
        super();
    }

}
