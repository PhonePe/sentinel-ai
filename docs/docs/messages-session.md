---
title: Chat Messages and Session Management
description: Managing conversation history and session summarization in Sentinel AI
---

# Chat Messages and Session Management

The `AgentSessionExtension` provides automated conversation history management and session summarization for Sentinel AI agents. It ensures that agents maintain context across multiple turns by persisting messages and injecting relevant history and summaries into subsequent interactions.

## Features

- **Automated Message Persistence**: Saves all user and agent messages to a configured storage backend.
- **Conversation Summarization**: Automatically generates and updates session summaries based on token usage thresholds.
- **Context Injection**: Injects the latest session summary as a fact in the system prompt.
- **History Retrieval**: Automatically retrieves and injects previous messages from the session history into the model context.
- **Pre-filtering**: Supports filtering messages before persistence (e.g., removing system prompts or failed tool calls).
- **Message Selection**: Supports selecting specific messages for context (e.g., removing unpaired tool calls).

## Tools

Unlike the Memory or Registry extensions, the `AgentSessionExtension` **does not expose any tools** to the agent. It operates strictly through automated background processes:
1.  **Fact Injection**: Injects the current session summary into the system prompt.
2.  **History Injection**: Prepends historical messages to the message list before calling the model.
3.  **Post-Processing**: Captures new messages and triggers summarization after the agent execution finishes.

## Configuration

The extension is configured using the `AgentSessionExtensionSetup` class.

| **Setting**                             | **Type**            | **Default** | **Description**                                                                                               |
|-----------------------------------------|---------------------|-------------|---------------------------------------------------------------------------------------------------------------|
| `historicalMessageFetchSize`            | `int`               | 30          | Number of historical messages to fetch in a single batch.                                                     |
| `maxSummaryLength`                      | `int`               | 1000        | Maximum character length for the generated session summary.                                                   |
| `autoSummarizationThresholdPercentage` | `int`               | 60          | Percentage of the model's context window usage that triggers automatic summarization. Set to 0 to summarize every run. |
| `compactionPrompts`                     | `CompactionPrompts` | DEFAULT     | Custom prompts used for the summarization process.                                                            |

## Usage

To use the session extension, add it to your agent's extension list during initialization.

```java
final var sessionExtension = AgentSessionExtension.<MyRequest, MyResponse, MyAgent>builder()
        .sessionStore(sessionStore) // Implementation of SessionStore
        .mapper(objectMapper)
        .setup(AgentSessionExtensionSetup.builder()
                .autoSummarizationThresholdPercentage(70)
                .build())
        .build();

final var agent = new MyAgent(setup, List.of(sessionExtension), Map.of());
```

!!! danger "Session ID Requirement"
    The session extension relies on a `sessionId` being present in the `AgentRequestMetadata`. If no `sessionId` is provided during execution, the extension will skip history persistence and retrieval.

```java
final var response = agent.execute(AgentInput.<MyRequest>builder()
        .request(new MyRequest(...))
        .requestMetadata(AgentRequestMetadata.builder()
                .sessionId("session-123")
                .userId("user-456")
                .build())
        .build());
```

## Session Storage Implementations

Sentinel AI provides multiple implementations for the `SessionStore` interface.

### Filesystem Storage

The `FileSystemSessionStore` stores session data and messages as JSON files on the local disk. This is suitable for development or single-node deployments.

**Dependency:**
```xml
<dependency>
    <groupId>com.phonepe.sentinel-ai</groupId>
    <artifactId>sentinel-ai-filesystem</artifactId>
</dependency>
```

**Implementation:**
```java
final var sessionStore = new FileSystemSessionStore("/path/to/storage", objectMapper);
```

### Elasticsearch Storage

The `ESSessionStore` provides a scalable implementation using Elasticsearch. It supports efficient searching and pagination of sessions and messages.

**Dependency:**
```xml
<dependency>
    <groupId>com.phonepe.sentinel-ai</groupId>
    <artifactId>sentinel-ai-storage-es</artifactId>
</dependency>
```

**Implementation:**
```java
final var esClient = ESClient.builder()
        .serverUrl("http://localhost:9200")
        .apiKey("optional-api-key")
        .build();

final var sessionStore = ESSessionStore.builder()
        .client(esClient)
        .mapper(objectMapper)
        .indexPrefix("my-application") // Optional prefix for indices
        .build();
```

## Automatic Summarization (Compaction)

One of the most powerful features of the session extension is its ability to automatically "compact" conversation history when it grows too large.

### How it Works
1. **Token Estimation**: After each agent run, the extension estimates the number of tokens in the current session history using the model's `estimateTokenCount` method.
2. **Threshold Evaluation**: It compares the estimated token count against the model's `contextWindowSize` (configured in `ModelSettings`).
3. **Trigger**: If `(estimatedTokens / contextWindowSize) * 100 > autoSummarizationThresholdPercentage`, the extension triggers a summarization task.
4. **Processing**: The summarization task runs asynchronously using the `MessageCompactor`.

### Model Attributes Configuration
To ensure accurate threshold evaluation, you should configure the `ModelAttributes` within your `ModelSettings`. This tells Sentinel AI about the specific limits and encoding of the model you are using.

```java
final var modelSettings = ModelSettings.builder()
        .modelAttributes(ModelAttributes.builder()
                .contextWindowSize(128_000) // (1)!
                .encodingType(EncodingType.CL100K_BASE) // (2)!
                .build())
        .temperature(0.7f)
        .build();
```

1.  **Context Window Size**: The total number of tokens the model can handle (e.g., 128k for GPT-4o).
2.  **Encoding Type**: The tokenizer used for counting (e.g., `CL100K_BASE` for OpenAI models, `O200K_BASE` for GPT-4o).

### Compaction Prompts Customization
You can customize how the summarization is performed by providing a `CompactionPrompts` object in the setup.

```java
final var customPrompts = CompactionPrompts.builder()
        .summarizationSystemPrompt("You are a expert at distilling chat history...")
        .summarizationUserPrompt("Please summarize these messages: ${sessionMessages}")
        .promptSchema(myJsonSchema) // (1)!
        .build();
```

1.  You can provide a custom JSON schema to change the structure of the generated summary.

### Default Summary Schema
By default, the compactor generates a structured output based on the following JSON schema:

```json
{
  "type": "object",
  "properties": {
    "title": { "type": "string" }, // (1)!
    "keywords": { // (2)!
      "type": "array",
      "items": { "type": "string" }
    },
    "summary": { "type": "string" }, // (3)!
    "key_points": { // (4)!
      "type": "array",
      "items": { "type": "string" }
    },
    "key_facts": { // (5)!
      "type": "array",
      "items": { "type": "string" }
    },
    "sentiment": { // (6)!
      "type": "string",
      "enum": ["positive", "neutral", "negative"]
    },
    "confidence": { "type": "number" } // (7)!
  },
  "required": ["title", "keywords", "summary", "key_points", "key_facts", "sentiment", "confidence"]
}
```

1.  **Title**: A human-readable heading capturing the essence of the conversation.
2.  **Keywords**: Relevant topics (1-3 tags).
3.  **Summary**: Concise narrative capturing context and outcomes.
4.  **Key Points**: Distilled takeaways ordered by importance.
5.  **Key Facts**: Objective, verifiable facts extracted from the source.
6.  **Sentiment**: Overall tone of the content.
7.  **Confidence**: Model-assessed confidence in the summary (0-10).

## Behaviour

### Conversation History
When an agent execution starts, the extension fetches historical messages from the `SessionStore`. It uses `MessageSelector` implementations (like `UnpairedToolCallsRemover`) to ensure the retrieved history is clean and coherent for the LLM.

### Facts Injection
The latest summary is injected into the system prompt as a fact:
```xml
<knowledge>
    <facts>
        <description>Information about session session-123</description>
        <fact>
            <name>A summary of the conversation in this session</name>
            <content>The user asked about book summaries and the agent provided details for War and Peace.</content>
        </fact>
    </facts>
</knowledge>
```

## Notes

- Summarization happens asynchronously after the main agent execution is completed to avoid increasing latency for the user.
- The extension automatically handles "compaction" when the model returns a `LENGTH_EXCEEDED` error, ensuring the next run has a summarized context.

## Warnings

- **Concurrent Access**: While storage implementations like `ESSessionStore` are thread-safe, concurrent updates to the same session summary might lead to race conditions where one summary overwrites another.
- **Storage Growth**: Without a cleanup policy, session and message indices can grow significantly. Ensure you have a data retention strategy in place.

## Caution: Footguns

!!! failure "Manual Message Management"
    Avoid passing `oldMessages` in `AgentInput` while using the `AgentSessionExtension`. The extension automatically retrieves history from the store. Providing `oldMessages` manually can result in duplicate messages or incorrect message ordering in the model prompt, as `oldMessages` are injected *before* the system prompt and extension-managed history.

!!! failure "Changing Model Context Windows"
    If you change the model used by an agent to one with a significantly smaller context window, existing sessions might fail with `LENGTH_EXCEEDED` before the automatic summarization can catch up. Monitor your token usage closely when switching models.
