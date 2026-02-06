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

package com.phonepe.sentinelai.core.compaction;

import lombok.experimental.UtilityClass;

/**
 * A compact representation of an agent message for compaction purposes
 */
@UtilityClass
public class CompactMessage {
    @UtilityClass
    public static final class Roles {
        public static final String SYSTEM = "system";
        public static final String USER = "user";
        public static final String ASSISTANT = "assistant";
    }

    @UtilityClass
    public static final class Types {
        public static final String CHAT = "chat";
        public static final String TOOL_CALL = "tool_call";
        public static final String TOOL_CALL_RESPONSE = "tool_call_response";
    }

}
