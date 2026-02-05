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

import java.util.List;

/**
 * A pre-processor of agent messages before the messages are sent to an LLM.
 *
 * The pre-processor can be used to modify the list of messages that are sent to a model. Common use cases that can
 * leverage this hook are compaction, inspection etc.
 */
@FunctionalInterface
public interface AgentMessagesPreProcessor {
    AgentMessagesPreProcessResult process(AgentMessagesPreProcessContext ctx, List<AgentMessage> allMessages,
            List<AgentMessage> newMessages);
}
