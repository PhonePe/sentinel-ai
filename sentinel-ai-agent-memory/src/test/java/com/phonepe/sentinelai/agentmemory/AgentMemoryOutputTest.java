package com.phonepe.sentinelai.agentmemory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AgentMemoryOutput} and its custom builder
 */
@Slf4j
class AgentMemoryOutputTest {

    private final ObjectMapper objectMapper = JsonUtils.createMapper();

    @Test
    void testBuilderCreation() {
        AgentMemoryOutput.AgentMemoryOutputBuilder builder = AgentMemoryOutput.builder();
        assertNotNull(builder);
    }

    @Test
    void testBuilderWithEmptyGeneratedMemory() {
        // Test building with empty list
        List<GeneratedMemoryUnit> emptyList = List.of();
        AgentMemoryOutput output = AgentMemoryOutput.builder()
                .generatedMemory(emptyList)
                .build();
        
        assertNotNull(output);
        assertNotNull(output.getGeneratedMemory());
        assertTrue(output.getGeneratedMemory().isEmpty());
    }

    @Test
    void testBuilderWithGeneratedMemory() {
        // Create sample memory units
        GeneratedMemoryUnit memory1 = new GeneratedMemoryUnit(
                MemoryScope.ENTITY,
                "user123",
                MemoryType.SEMANTIC,
                "UserName",
                "The user's name is John Doe",
                List.of("user", "personal"),
                8
        );

        GeneratedMemoryUnit memory2 = new GeneratedMemoryUnit(
                MemoryScope.AGENT,
                "agent-proc",
                MemoryType.PROCEDURAL,
                "WeatherQuery",
                "When user asks about weather, first check their location",
                List.of("weather", "procedure"),
                7
        );

        List<GeneratedMemoryUnit> memories = Arrays.asList(memory1, memory2);

        // Test building with generated memories
        AgentMemoryOutput output = AgentMemoryOutput.builder()
                .generatedMemory(memories)
                .build();
        
        assertNotNull(output);
        assertNotNull(output.getGeneratedMemory());
        assertEquals(2, output.getGeneratedMemory().size());
        assertEquals(memories, output.getGeneratedMemory());
    }

    @Test
    void testBuilderToString() {
        // Test builder toString method
        AgentMemoryOutput.AgentMemoryOutputBuilder builder = AgentMemoryOutput.builder();
        String builderString = builder.toString();
        
        assertNotNull(builderString);
        assertTrue(builderString.contains("AgentMemoryOutputBuilder"));
        assertTrue(builderString.contains("generatedMemory=null"));
    }

    @Test
    void testBuilderEqualsAndHashCode() {
        // Test that two empty outputs are equal
        AgentMemoryOutput output1 = AgentMemoryOutput.builder().build();
        AgentMemoryOutput output2 = AgentMemoryOutput.builder().build();
        
        assertEquals(output1, output2);
        assertEquals(output1.hashCode(), output2.hashCode());
    }

    @Test
    void testJsonSerialization() throws Exception {
        // Test JSON serialization/deserialization
        GeneratedMemoryUnit memory = new GeneratedMemoryUnit(
                MemoryScope.ENTITY,
                "user123",
                MemoryType.SEMANTIC,
                "UserPreference",
                "User prefers dark mode",
                List.of("ui", "preference"),
                6
        );

        AgentMemoryOutput original = AgentMemoryOutput.builder()
                .generatedMemory(List.of(memory))
                .build();

        // Serialize to JSON
        String json = objectMapper.writeValueAsString(original);
        assertNotNull(json);
        assertTrue(json.contains("UserPreference"));
        assertTrue(json.contains("dark mode"));

        // Deserialize back
        AgentMemoryOutput deserialized = objectMapper.readValue(json, AgentMemoryOutput.class);
        assertNotNull(deserialized);
        assertEquals(original, deserialized);
    }

    @Test
    void testCustomBuilderClassName() {
        // Test that the custom builder class name is correctly set
        // This is important for Jackson deserialization
        AgentMemoryOutput.AgentMemoryOutputBuilder builder = AgentMemoryOutput.builder();
        assertEquals("AgentMemoryOutputBuilder", builder.getClass().getSimpleName());
    }

    @Test
    void testBuilderWithComplexMemoryStructure() {
        // Test with a more complex memory structure
        List<GeneratedMemoryUnit> memories = Arrays.asList(
                new GeneratedMemoryUnit(
                        MemoryScope.ENTITY,
                        "user456",
                        MemoryType.SEMANTIC,
                        "PersonalInfo",
                        "User is a software engineer from India working on AI systems",
                        Arrays.asList("personal", "profession", "location", "technology"),
                        9
                ),
                new GeneratedMemoryUnit(
                        MemoryScope.ENTITY,
                        "user456",
                        MemoryType.EPISODIC,
                        "PreviousConversation",
                        "User asked about best practices for microservices architecture yesterday",
                        Arrays.asList("conversation", "microservices", "architecture"),
                        7
                ),
                new GeneratedMemoryUnit(
                        MemoryScope.AGENT,
                        "sentiment-analyzer",
                        MemoryType.PROCEDURAL,
                        "SentimentAnalysis",
                        "When analyzing sentiment, consider cultural context and technical jargon",
                        Arrays.asList("sentiment", "analysis", "cultural", "technical"),
                        8
                )
        );

        AgentMemoryOutput output = AgentMemoryOutput.builder()
                .generatedMemory(memories)
                .build();

        assertNotNull(output);
        assertEquals(3, output.getGeneratedMemory().size());
        
        // Verify different memory types and scopes are preserved
        long entityMemories = output.getGeneratedMemory().stream()
                .filter(m -> m.getScope() == MemoryScope.ENTITY)
                .count();
        long agentMemories = output.getGeneratedMemory().stream()
                .filter(m -> m.getScope() == MemoryScope.AGENT)
                .count();
        
        assertEquals(2, entityMemories);
        assertEquals(1, agentMemories);
    }
}
