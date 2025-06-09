package com.phonepe.sentinelai.storage.memory;

import com.phonepe.sentinelai.agentmemory.AgentMemory;
import com.phonepe.sentinelai.agentmemory.MemoryScope;
import com.phonepe.sentinelai.embedding.HuggingfaceEmbeddingModel;
import com.phonepe.sentinelai.storage.ESClient;
import com.phonepe.sentinelai.storage.ESIntegrationTestBase;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

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
                .apiKey("test")
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
                                                     "name", 0, 10);
            log.debug("Results: {}", results);
            assertTrue(!results.isEmpty());
        }

    }

}