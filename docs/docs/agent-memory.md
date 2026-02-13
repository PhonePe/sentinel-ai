---
title: Agent Memory
description: Managing persistent agent memory across sessions in Sentinel AI
---

# Agent Memory

Agent Memory is a powerful feature that allows agents to remember information across different sessions and users. Unlike conversation history (which is session-specific and typically ephemeral), Agent Memory extracts semantic, episodic, or procedural information and stores it in a persistent store for long-term retrieval.

## Agent Memory vs. Conversation History

It is important to distinguish between these two:

| Feature | Conversation History | Agent Memory |
|---------|----------------------|--------------|
| **Scope** | Current Session | Cross-session / Cross-user |
| **Storage** | Typically RAM or Session Store | Vector Database or File System |
| **Retrieval** | All messages sent to LLM | Semantic search based on relevance |
| **Content** | Raw messages | Extracted facts, procedures, and events |

## Agent Memory Extension

The `AgentMemoryExtension` enables memory capabilities. It handles memory retrieval (injecting relevant facts into the prompt) and memory extraction (learning from the current conversation).

### Configuration Options

The `AgentMemoryExtension` can be configured using its builder:

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `memoryStore` | `AgentMemoryStore` | **Required** | The storage implementation for memories. |
| `memoryExtractionMode` | `MemoryExtractionMode` | `INLINE` | How memories are extracted. See [Extraction Modes](#memory-extraction-modes). |
| `minRelevantReusabilityScore` | `int` | `0` | Minimum score (0-10) for a memory to be saved or retrieved. Helps filter "noise". |
| `objectMapper` | `ObjectMapper` | Default Mapper | Used for serializing memory units. |

### Example Setup

```java
final var memoryExtension = AgentMemoryExtension.builder()
        .memoryStore(memoryStore)
        .memoryExtractionMode(MemoryExtractionMode.INLINE)
        .minRelevantReusabilityScore(7) // Only "highly reusable" memories
        .build();

final var agent = new MyAgent(AgentSetup.builder()
        .model(model)
        .extension(memoryExtension)
        .build());
```

## Memory Tools

The `AgentMemoryExtension` provides a specialized tool for semantic memory retrieval.

| Tool Name | Description | Parameters |
|-----------|-------------|------------|
| `agent_memory_extension_find_memories` | Retrieves relevant memories from the persistent store based on a natural language query. | `query` (String) |

When this extension is registered, the agent is instructed to use this tool to check for relevant background information before proceeding with complex tasks.

## Memory Extraction Modes

The `MemoryExtractionMode` determines the lifecycle of memory creation:

| Mode | Description |
|------|-------------|
| `INLINE` | Extraction happens during the primary model call using structured output. **Nuance:** This is the most efficient mode but is **not supported** in streaming mode. Sentinel AI will automatically force an out-of-band extraction if the agent is run in streaming mode. |
| `OUT_OF_BAND` | Extraction happens as a separate, asynchronous model call after the primary response is generated. This ensures extraction works even with streaming. |
| `DISABLED` | No new memories are extracted. Useful for read-only memory agents. |

## Storage Implementations

Sentinel AI requires an `EmbeddingModel` to generate vector representations of memories for semantic search.

### File System Storage (`sentinel-ai-filesystem`)

Ideal for local development or small-scale applications. It stores memories as JSON files and vectors in a local directory.

```java
final var storage = FileSystemAgentMemoryStorage.builder()
        .baseDir("./data/memory")
        .mapper(objectMapper)
        .embeddingModel(embeddingModel) // e.g., LocalEmbeddingModel or OpenAIEmbeddingModel
        .build();
```

!!!warning "Performance"
    `FileSystemAgentMemoryStorage` performs a linear scan and manual cosine similarity calculation for search. It is not intended for production use with thousands of memories.

### Elasticsearch Storage (`sentinel-ai-storage-es`)

Recommended for production. Uses Elasticsearch's native KNN (k-nearest neighbors) search for efficient retrieval.

```java
final var storage = ESAgentMemoryStorage.builder()
        .client(esClient)
        .embeddingModel(embeddingModel)
        .indexPrefix("prod") // Optional: prefixes the 'agent-memories' index
        .build();
```

!!!note "Vector Dimensions"
    The Elasticsearch implementation automatically determines vector dimensions based on the provided `EmbeddingModel` during initial index creation. If you change your embedding model later, you may need to recreate the index to match the new dimension count.

## Tips and Nuances

*   **Reusability Scores**: The LLM assigns a reusability score to each extracted memory. Use `minRelevantReusabilityScore` (e.g., `7`) to prevent your store from being cluttered with session-specific trivia.
*   **Memory Scopes**: 
    *   **`AGENT`**: Shared knowledge (e.g., "Field 'X' in the database refers to User Salary").
    *   **`ENTITY`**: User-specific (e.g., "User prefers dark mode").
*   **Facts Injection**: Memories are injected as `Facts` into the system prompt. This happens automatically based on the `userId` provided in `AgentRequestMetadata`.

## Dangers and Risks

*   **PII & Privacy**: Since memories are stored persistently across sessions, be extremely careful about extracting Personal Identifiable Information (PII). You can use a `AgentMessagesPreProcessor` to mask data before it reaches the extraction task.
*   **Hallucinations**: The LLM might "remember" things that weren't explicitly stated or were misunderstood. Periodic auditing of the memory store is recommended.
*   **Token Overhead**: Retrieving too many memories (high `count`) can bloat your system prompt and increase costs/latency.
*   **Embedding Costs**: Every save and every search requires an embedding model call. If using remote models (like OpenAI), this adds to your per-request cost.
