/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    TOOL_CALL_APPROVAL_DENIED(Values.TOOL_CALL_APPROVAL_DENIED),
    TOOL_CALLED(Values.TOOL_CALLED),
    TOOL_CALL_COMPLETED(Values.TOOL_CALL_COMPLETED),
    OUTPUT_GENERATED(Values.OUTPUT_GENERATED),
    ;

    private final String type;

    EventType(String type) {
        this.type = type;
    }

    @UtilityClass
    public static final class Values {
        public static final String MESSAGE_RECEIVED = "MESSAGE_RECEIVED";
        public static final String MESSAGE_SENT = "MESSAGE_SENT";
        public static final String TOOL_CALL_APPROVAL_DENIED = "TOOL_CALL_APPROVAL_DENIED";
        public static final String TOOL_CALLED = "TOOL_CALLED";
        public static final String TOOL_CALL_COMPLETED = "TOOL_CALL_COMPLETED";
        public static final String OUTPUT_GENERATED = "OUTPUT_GENERATED";
    }
}
