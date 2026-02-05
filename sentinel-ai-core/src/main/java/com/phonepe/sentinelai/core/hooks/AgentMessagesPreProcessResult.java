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

package com.phonepe.sentinelai.core.hooks;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;

import lombok.Value;

import java.util.List;

/**
 * Output of a pre-processor of agent messages.
 *
 * If the transformedMessages is set as null or empty list by a processor, it is a NOOP and the agent messages
 * will not be modified. Completely resetting agent messages to an empty list by a processor is not allowed.
 */
@Value
public class AgentMessagesPreProcessResult {
    List<AgentMessage> transformedMessages;

    List<AgentMessage> newMessages;
}
