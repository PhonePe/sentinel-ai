package com.phonepe.sentinelai.core.events;

import lombok.Getter;
import lombok.experimental.UtilityClass;

/**
 * The different event types for the agent
 */
@Getter
public enum EventType {
    MESSAGE_RECEIVED(Values.MESSAGE_RECEIVED),
    MESSAGE_SENT(Values.MESSAGE_SENT),
    TOOL_CALLED(Values.TOOL_CALLED),
    TOOL_CALL_COMPLETED(Values.TOOL_CALL_COMPLETED)
    ;

    private final String type;

    EventType(String type) {
        this.type = type;
    }

    @UtilityClass
    public static final class Values {
        public static final String MESSAGE_RECEIVED = "MESSAGE_RECEIVED";
        public static final String MESSAGE_SENT = "MESSAGE_SENT";
        public static final String TOOL_CALLED = "TOOL_CALLED";
        public static final String TOOL_CALL_COMPLETED = "TOOL_CALL_COMPLETED";
    }
}
