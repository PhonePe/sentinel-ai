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

package com.phonepe.sentinelai.models.wire;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;

import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Chain of {@link AgentMessage} level transforms that run before translation in the model loop.
 * Use for sentinel-level transforms (prompt rewriting, redaction) that must not know about wire
 * formats. The chain runs once per message translation pass; translated nodes are then shared
 * across turns untouched.
 */
@FunctionalInterface
public interface MessageTransformer {

    /**
     * Applies the given transformers in order to the whole message list.
     */
    static List<AgentMessage> applyAll(final List<AgentMessage> messages,
                                       final List<UnaryOperator<AgentMessage>> transformers) {
        if (transformers == null || transformers.isEmpty()) {
            return messages;
        }
        var result = messages;
        for (final var transformer : transformers) {
            result = result.stream().map(transformer::apply).toList();
        }
        return result;
    }

    /**
     * Transforms one message; return the original instance when no change is needed.
     */
    AgentMessage transform(AgentMessage message);
}
