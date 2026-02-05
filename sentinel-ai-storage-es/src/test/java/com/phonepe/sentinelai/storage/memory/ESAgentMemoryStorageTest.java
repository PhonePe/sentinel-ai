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

package com.phonepe.sentinelai.storage.memory;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.phonepe.sentinelai.agentmemory.AgentMemory;
import com.phonepe.sentinelai.agentmemory.MemoryScope;
import com.phonepe.sentinelai.core.utils.TestUtils;
import com.phonepe.sentinelai.embedding.HuggingfaceEmbeddingModel;
import com.phonepe.sentinelai.storage.ESClient;
import com.phonepe.sentinelai.storage.ESIntegrationTestBase;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.EnumSet;
import java.util.List;

import static com.phonepe.sentinelai.agentmemory.MemoryType.SEMANTIC;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for {@link ESAgentMemoryDocument}
 */
@Testcontainers
@Slf4j
class ESAgentMemoryStorageTest extends ESIntegrationTestBase {


    @Test
    @SneakyThrows
    void test() {
        try (final var client = ESClient.builder()
                .serverUrl(ELASTICSEARCH_CONTAINER.getHttpHostAddress())
                .apiKey(TestUtils.getTestProperty("ES_API_KEY", "test"))
                .build()) {

            final var storage = new ESAgentMemoryStorage(client,
                                                         new HuggingfaceEmbeddingModel(),
                                                         indexPrefix(this));
            {
                final var saved = storage.save(AgentMemory.builder()
                        .scope(MemoryScope.ENTITY)
                        .memoryType(SEMANTIC)
                        .scopeId("TestUser")
                        .name("UserName")
                        .content("User's name is santanu")
                        .topics(List.of("info"))
                        .reusabilityScore(10)
                        .build());
                assertTrue(saved.isPresent());
            }
            {
                final var saved = storage.save(AgentMemory.builder()
                        .scope(MemoryScope.ENTITY)
                        .memoryType(SEMANTIC)
                        .scopeId("TestUser")
                        .name("UserLocation")
                        .content("User's location is bangalore")
                        .topics(List.of("info"))
                        .reusabilityScore(10)
                        .build());
                assertTrue(saved.isPresent());
            }
            final var results = storage.findMemories("TestUser",
                                                     MemoryScope.ENTITY,
                                                     EnumSet.of(SEMANTIC),
                                                     List.of(),
                                                     "name",
                                                     0,
                                                     10);
            log.debug("Results: {}", results);
            assertTrue(!results.isEmpty());
        }

    }

}
