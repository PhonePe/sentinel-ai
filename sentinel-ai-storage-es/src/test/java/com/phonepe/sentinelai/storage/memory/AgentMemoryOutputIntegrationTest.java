package com.phonepe.sentinelai.storage.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.agentmemory.*;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AgentMemoryOutput builder working with InMemoryMemoryStorage
 */
@Slf4j
class AgentMemoryOutputIntegrationTest {

    private ObjectMapper objectMapper;
    private InMemoryMemoryStorage memoryStorage;

    @BeforeEach
    void setUp() {
        objectMapper = JsonUtils.createMapper();
        memoryStorage = new InMemoryMemoryStorage();
    }

    @Test
    void testAgentMemoryOutputBuilderWithInMemoryStorage() throws Exception {
        // Create AgentMemoryOutput using the custom builder
        GeneratedMemoryUnit memory1 = new GeneratedMemoryUnit(
                MemoryScope.ENTITY,
                "user123",
                MemoryType.SEMANTIC,
                "UserName",
                "The user's name is Alice Johnson",
                List.of("personal", "identity"),
                8
        );

        GeneratedMemoryUnit memory2 = new GeneratedMemoryUnit(
                MemoryScope.ENTITY,
                "user123",
                MemoryType.SEMANTIC,
                "UserPreference",
                "User prefers email notifications over SMS",
                List.of("preferences", "communication"),
                7
        );

        AgentMemoryOutput memoryOutput = AgentMemoryOutput.builder()
                .generatedMemory(Arrays.asList(memory1, memory2))
                .build();

        assertNotNull(memoryOutput);
        assertEquals(2, memoryOutput.getGeneratedMemory().size());

        // Convert memories to AgentMemory objects and save to storage
        for (GeneratedMemoryUnit generatedMemory : memoryOutput.getGeneratedMemory()) {
            AgentMemory agentMemory = convertToAgentMemory(generatedMemory, "test-agent");
            memoryStorage.save(agentMemory);
        }

        // Verify memories were saved to InMemoryStorage
        List<AgentMemory> savedMemories = memoryStorage.findMemories(
                "user123", MemoryScope.ENTITY, null, null, null, 0, 10
        );

        assertEquals(2, savedMemories.size());

        // Check that the memories have correct content
        boolean foundUserName = savedMemories.stream()
                .anyMatch(m -> "UserName".equals(m.getName()) && 
                              "The user's name is Alice Johnson".equals(m.getContent()));
        boolean foundUserPreference = savedMemories.stream()
                .anyMatch(m -> "UserPreference".equals(m.getName()) &&
                              "User prefers email notifications over SMS".equals(m.getContent()));

        assertTrue(foundUserName);
        assertTrue(foundUserPreference);

        // Verify agent name was set correctly
        assertTrue(savedMemories.stream().allMatch(m -> "test-agent".equals(m.getAgentName())));
    }

    @Test
    void testJsonSerializationAndStorage() throws Exception {
        // Test complete JSON serialization/deserialization cycle
        GeneratedMemoryUnit memory = new GeneratedMemoryUnit(
                MemoryScope.AGENT,
                "proc-context",
                MemoryType.PROCEDURAL,
                "TaskProcess",
                "When handling user queries, first check existing memories for context",
                List.of("process", "query", "context"),
                9
        );

        AgentMemoryOutput original = AgentMemoryOutput.builder()
                .generatedMemory(List.of(memory))
                .build();

        // Serialize to JSON
        String json = objectMapper.writeValueAsString(original);
        
        // Deserialize back
        AgentMemoryOutput deserialized = objectMapper.readValue(json, AgentMemoryOutput.class);
        
        // Verify they're equal
        assertEquals(original, deserialized);

        // Convert and save to storage
        GeneratedMemoryUnit deserializedMemory = deserialized.getGeneratedMemory().get(0);
        AgentMemory agentMemory = convertToAgentMemory(deserializedMemory, "test-agent");
        memoryStorage.save(agentMemory);

        // Verify saved in memory storage
        List<AgentMemory> savedMemories = memoryStorage.findMemories(
                "proc-context", MemoryScope.AGENT, null, null, null, 0, 10
        );

        assertEquals(1, savedMemories.size());
        AgentMemory savedMemory = savedMemories.get(0);
        assertEquals("TaskProcess", savedMemory.getName());
        assertEquals("When handling user queries, first check existing memories for context", 
                     savedMemory.getContent());
        assertEquals(MemoryType.PROCEDURAL, savedMemory.getMemoryType());
        assertEquals(9, savedMemory.getReusabilityScore());
    }

    @Test
    void testMultipleMemoryScopes() throws Exception {
        // Test with memories from different scopes
        GeneratedMemoryUnit entityMemory = new GeneratedMemoryUnit(
                MemoryScope.ENTITY,
                "user456",
                MemoryType.SEMANTIC,
                "UserJob",
                "User works as a data scientist",
                List.of("profession", "career"),
                8
        );

        GeneratedMemoryUnit agentMemory = new GeneratedMemoryUnit(
                MemoryScope.AGENT,
                "agent-learning",
                MemoryType.PROCEDURAL,
                "DataScienceHelp",
                "When user asks about data science, provide practical examples",
                List.of("assistance", "examples"),
                7
        );

        AgentMemoryOutput memoryOutput = AgentMemoryOutput.builder()
                .generatedMemory(Arrays.asList(entityMemory, agentMemory))
                .build();

        // Save to storage
        for (GeneratedMemoryUnit generatedMemory : memoryOutput.getGeneratedMemory()) {
            AgentMemory converted = convertToAgentMemory(generatedMemory, "test-agent");
            memoryStorage.save(converted);
        }

        // Verify entity memories
        List<AgentMemory> entityMemories = memoryStorage.findMemories(
                "user456", MemoryScope.ENTITY, null, null, null, 0, 10
        );
        assertEquals(1, entityMemories.size());
        assertEquals("UserJob", entityMemories.get(0).getName());

        // Verify agent memories
        List<AgentMemory> agentMemories = memoryStorage.findMemories(
                "agent-learning", MemoryScope.AGENT, null, null, null, 0, 10
        );
        assertEquals(1, agentMemories.size());
        assertEquals("DataScienceHelp", agentMemories.get(0).getName());
    }

    @Test
    void testComplexMemoryStructureIntegration() throws Exception {
        // Test the custom builder with complex memory structures
        List<GeneratedMemoryUnit> complexMemories = Arrays.asList(
                new GeneratedMemoryUnit(
                        MemoryScope.ENTITY,
                        "company123",
                        MemoryType.SEMANTIC,
                        "CompanyInfo",
                        "TechCorp is a software company specializing in AI solutions",
                        Arrays.asList("company", "business", "ai", "technology"),
                        9
                ),
                new GeneratedMemoryUnit(
                        MemoryScope.ENTITY,
                        "company123",
                        MemoryType.EPISODIC,
                        "RecentMeeting",
                        "Had a productive meeting about Q4 goals last Tuesday",
                        Arrays.asList("meeting", "goals", "quarterly"),
                        6
                ),
                new GeneratedMemoryUnit(
                        MemoryScope.AGENT,
                        "business-context",
                        MemoryType.PROCEDURAL,
                        "BusinessCommunication",
                        "When discussing business topics, use professional language and focus on ROI",
                        Arrays.asList("communication", "business", "professional"),
                        8
                )
        );

        AgentMemoryOutput complexOutput = AgentMemoryOutput.builder()
                .generatedMemory(complexMemories)
                .build();

        // Verify serialization/deserialization works
        String json = objectMapper.writeValueAsString(complexOutput);
        AgentMemoryOutput deserialized = objectMapper.readValue(json, AgentMemoryOutput.class);
        assertEquals(complexOutput, deserialized);

        // Save to storage
        for (GeneratedMemoryUnit generatedMemory : deserialized.getGeneratedMemory()) {
            AgentMemory converted = convertToAgentMemory(generatedMemory, "test-agent");
            memoryStorage.save(converted);
        }

        // Verify all memories were saved correctly
        List<AgentMemory> companyMemories = memoryStorage.findMemories(
                "company123", MemoryScope.ENTITY, null, null, null, 0, 10
        );
        assertEquals(2, companyMemories.size());

        List<AgentMemory> agentMemories = memoryStorage.findMemories(
                "business-context", MemoryScope.AGENT, null, null, null, 0, 10
        );
        assertEquals(1, agentMemories.size());

        // Verify all topics are preserved
        AgentMemory companyInfo = companyMemories.stream()
                .filter(m -> "CompanyInfo".equals(m.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(companyInfo.getTopics().contains("ai"));
        assertTrue(companyInfo.getTopics().contains("technology"));
        assertEquals(4, companyInfo.getTopics().size());
    }

    @Test
    void testEmptyAndNullMemoryLists() throws Exception {
        // Test with empty memory list
        AgentMemoryOutput emptyOutput = AgentMemoryOutput.builder()
                .generatedMemory(List.of())
                .build();

        assertNotNull(emptyOutput);
        assertTrue(emptyOutput.getGeneratedMemory().isEmpty());

        // Test with null memory list
        AgentMemoryOutput nullOutput = AgentMemoryOutput.builder()
                .generatedMemory(null)
                .build();

        assertNotNull(nullOutput);
        assertNull(nullOutput.getGeneratedMemory());

        // Verify storage remains empty
        List<AgentMemory> allMemories = memoryStorage.findMemories(
                null, null, null, null, null, 0, 100
        );
        assertTrue(allMemories.isEmpty());
    }

    @Test
    void testBuilderCustomClassNameWithStorage() {
        // Test that the custom builder class name works correctly with storage operations
        AgentMemoryOutput.AgentMemoryOutputBuilder builder = AgentMemoryOutput.builder();
        assertEquals("AgentMemoryOutputBuilder", builder.getClass().getSimpleName());

        GeneratedMemoryUnit testMemory = new GeneratedMemoryUnit(
                MemoryScope.ENTITY,
                "builder-test",
                MemoryType.SEMANTIC,
                "BuilderTest",
                "Testing custom builder integration",
                List.of("test", "builder"),
                7
        );

        AgentMemoryOutput output = builder.generatedMemory(List.of(testMemory)).build();
        
        // Save to storage
        AgentMemory converted = convertToAgentMemory(testMemory, "builder-test-agent");
        memoryStorage.save(converted);

        // Verify it was saved correctly
        List<AgentMemory> saved = memoryStorage.findMemories(
                "builder-test", MemoryScope.ENTITY, null, null, null, 0, 10
        );
        assertEquals(1, saved.size());
        assertEquals("BuilderTest", saved.get(0).getName());
    }

    /**
     * Helper method to convert GeneratedMemoryUnit to AgentMemory
     */
    private AgentMemory convertToAgentMemory(GeneratedMemoryUnit generatedMemory, String agentName) {
        return AgentMemory.builder()
                .agentName(agentName)
                .scope(generatedMemory.getScope())
                .scopeId(generatedMemory.getScopeId())
                .memoryType(generatedMemory.getType())
                .name(generatedMemory.getName())
                .content(generatedMemory.getContent())
                .topics(generatedMemory.getTopics())
                .reusabilityScore(generatedMemory.getReusabilityScore())
                .build();
    }
}
