---
title: Agent Memory
description: Managing persistent agent memory across sessions in Sentinel AI
---

# Agent Memory

Agent Memory is a powerful feature that allows agents to remember information across different sessions and users. Unlike conversation history (which is session-specific), Agent Memory extracts semantic, episodic, or procedural information and stores it for long-term retrieval.

## Agent Memory Extension

The `AgentMemoryExtension` is used to enable memory capabilities for an agent. It handles:
1. **Memory Retrieval**: Searching and injecting relevant memories into the system prompt before processing a request.
2. **Memory Extraction**: Analyzing the conversation after a request is completed to extract new memories.

### Configuration

To use Agent Memory, you need to add the `sentinel-ai-agent-memory` dependency:

```xml
<dependency>
    <groupId>com.phonepe.sentinel-ai</groupId>
    <artifactId>sentinel-ai-agent-memory</artifactId>
</dependency>
```

Then, configure the extension in your `Agent` or `AgentSetup`:

```java
final var memoryStore = FileSystemAgentMemoryStorage.builder()
        .basePath("/path/to/memory/store")
        .build();

final var memoryExtension = AgentMemoryExtension.builder()
        .memoryStore(memoryStore)
        .memoryExtractionMode(MemoryExtractionMode.INLINE) // Extract memory during the main model call
        .minRelevantReusabilityScore(7) // Only store/retrieve high-quality memories
        .build();

final var agent = new MyAgent(AgentSetup.builder()
        .model(model)
        .extension(memoryExtension)
        .build());
```

## Memory Extraction Modes

The `MemoryExtractionMode` determines how and when memories are extracted from the conversation:

| Mode | Description |
|------|-------------|
| `INLINE` | Extraction is performed as a secondary task in the same model call as the primary request. This is efficient but only supported in `DIRECT` (non-streaming) mode. |
| `OUT_OF_BAND` | Extraction is performed as an asynchronous, separate model call after the primary response is generated. This is required for streaming mode. |
| `DISABLED` | No new memories are extracted, though existing memories can still be retrieved. |

## Memory Scopes

Memories can be stored with different scopes to control their visibility:

* **`AGENT`**: Shared across all users of the agent. Useful for "learning" facts about the agent's domain or tools.
* **`ENTITY`**: Scoped to a specific user or entity (e.g., a customer ID). These memories are only retrieved when the same `userId` or `entityId` is present in the `AgentRequestMetadata`.

## Memory Types

Sentinel AI categorizes memories into three types:

1. **`SEMANTIC`**: General facts (e.g., "The user lives in Bangalore").
2. **`EPISODIC`**: Specific events or interactions (e.g., "The user complained about a delayed order on Tuesday").
3. **`PROCEDURAL`**: Learned sequences of actions or preferences (e.g., "The user prefers to get summary before detail").

## Storage Implementations

Sentinel AI provides several storage implementations for Agent Memory:

### File System Storage
Available in `sentinel-ai-filesystem`. Ideal for local development or small-scale applications.

```java
final var storage = FileSystemAgentMemoryStorage.builder()
        .basePath("./data/memory")
        .build();
```

### Elasticsearch Storage
Available in `sentinel-ai-storage-es`. Recommended for production use cases requiring fast vector search or high scalability.

```java
final var storage = ESAgentMemoryStorage.builder()
        .client(esClient)
        .index("agent_memories")
        .build();
```
