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

package com.phonepe.sentinelai.core.agentmessages.requests;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.utils.JsonUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Locks in the cache-stability guarantee for the per-user-message send time: a {@link UserPrompt}
 * carrying a {@code sentAt} must round-trip through the session store (Jackson) byte-identically, so
 * a replayed history turn produces exactly the same prompt prefix and does not bust the LLM prompt
 * cache.
 */
class UserPromptSerdeTest {

    private final ObjectMapper mapper = JsonUtils.createMapper();

    @Test
    void repeatedSerializationIsStable() throws Exception {
        final var prompt = new UserPrompt("session-1",
                                          "run-1",
                                          "<user_input><data>hi</data></user_input>",
                                          false,
                                          LocalDateTime.of(2026, 7, 25, 10, 0, 0));

        assertEquals(mapper.writeValueAsString(prompt), mapper.writeValueAsString(prompt));
    }

    @Test
    void sentAtSurvivesRoundTripUnchanged() throws Exception {
        final var original = new UserPrompt("session-1",
                                            "run-1",
                                            "<user_input><data>hello</data></user_input>",
                                            false,
                                            LocalDateTime.of(2026, 7, 25, 10, 0, 0));

        final var json = mapper.writeValueAsString(original);
        final var revived = mapper.readValue(json, UserPrompt.class);

        assertEquals(original.getSentAt(), revived.getSentAt());
        assertEquals(original.getContent(), revived.getContent());

        // Serializing the revived message must be byte-identical to the first serialization.
        assertEquals(json, mapper.writeValueAsString(revived));
    }
}
