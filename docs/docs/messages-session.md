---
title: Chat Messages and Session Management
description: Managing conversation history and session persistence in Sentinel AI
---

# Chat Messages and Session Management

The `AgentSessionExtension` provides automated conversation history persistence and retrieval for Sentinel AI agents. It ensures that agents maintain context across multiple turns by storing messages to a configured backend and injecting relevant history into subsequent interactions.

!!!tip "Automatic Compaction"
    For automatic message history compaction to prevent context window overflow, see the [Auto Compaction Setup](agents.md#auto-compaction-setup) section in the Agents documentation. Auto compaction is configured at the agent level, not through the session extension.

## Features

- **Automated Message Persistence**: Saves all user and agent messages to a configured storage backend.
- **History Retrieval**: Automatically retrieves and injects previous messages from the session history into the model context.
- **Manual Compaction**: Provides `forceCompaction()` method for explicit conversation summarization.
- **Emergency Compaction**: Automatically triggers compaction when the model returns a `LENGTH_EXCEEDED` error.
- **Initial Summarization**: Generates a summary at the start of a session when `preSummarizationDisabled` is false.
- **Context Injection**: Injects the latest session summary as a fact in the system prompt.
- **Pre-filtering**: Supports filtering messages before persistence (e.g., removing system prompts or failed tool calls).
- **Message Selection**: Supports selecting specific messages for context (e.g., removing unpaired tool calls).

## Tools

Unlike the Memory or Registry extensions, the `AgentSessionExtension` **does not expose any tools** to the agent. It operates strictly through automated background processes:

1. **Fact Injection**: Injects the current session summary into the system prompt.
2. **History Injection**: Prepends historical messages to the message list before calling the model.
3. **Post-Processing**: Captures new messages after agent execution finishes.

## Configuration

The extension is configured using the `AgentSessionExtensionSetup` class.

| **Setting**                    | **Type**  | **Default** | **Description**                                                                                         |
|--------------------------------|-----------|-------------|---------------------------------------------------------------------------------------------------------|
| `historicalMessageFetchSize`   | `int`     | 30          | Number of historical messages to fetch in a single batch when retrieving session history.               |
| `preSummarizationDisabled`     | `boolean` | false       | If false, generates an initial summary at session start. Set to true to disable initial summarization. |

## Usage

To use the session extension, add it to your agent's extension list during initialization.

```java
final var sessionExtension = AgentSessionExtension.<MyRequest, MyResponse, MyAgent>builder()
        .sessionStore(sessionStore) // Implementation of SessionStore
        .mapper(objectMapper)
        .setup(AgentSessionExtensionSetup.builder()
                .historicalMessageFetchSize(50)
                .preSummarizationDisabled(false)
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

## Manual Compaction

The session extension provides a `forceCompaction()` method for manually triggering conversation summarization. This is useful when you want explicit control over when summaries are generated.

```java
// Force compaction for a session
sessionExtension.forceCompaction("session-123", agentSetup);
```

The `forceCompaction()` method:

- Reads all messages from the session
- Invokes the `MessageCompactor` to generate a structured summary
- Stores the summary back to the session store
- Returns the generated summary

!!!tip "Automatic Compaction"
    For most use cases, configure [Auto Compaction](agents.md#auto-compaction-setup) at the agent level instead of manually calling `forceCompaction()`. Auto compaction runs proactively as a pre-processor before messages are sent to the LLM.

## Conversation Summarization

The session extension uses `MessageCompactor` to generate structured summaries of conversation history. Summaries are stored in the session and injected as facts in subsequent agent runs.

### Default Summary Schema

By default, the compactor generates a structured output based on the following JSON schema:

```json
{
  "type": "object",
  "additionalProperties": false,
  "strict": true,
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
    "action_items": { // (6)!
      "type": "array",
      "items": { "type": "string" }
    },
    "goal": { "type": "string" }, // (7)!
    "discoveries": { // (8)!
      "type": "array",
      "items": { "type": "string" }
    },
    "accomplishments": { // (9)!
      "type": "array",
      "items": { "type": "string" }
    },
    "relevant_files": { // (10)!
      "type": "array",
      "items": { "type": "string" }
    },
    "citations": { // (11)!
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "source": { "type": "string" },
          "quote": { "type": "string" }
        },
        "required": ["source", "quote"]
      }
    },
    "sentiment": { // (12)!
      "type": "string",
      "enum": ["positive", "neutral", "negative"]
    },
    "confidence": { "type": "number" }, // (13)!
    "metadata": { "type": "string" } // (14)!
  },
  "required": [
    "title", "keywords", "summary", "key_points", "key_facts",
    "action_items", "citations", "sentiment", "confidence",
    "metadata", "goal", "discoveries", "accomplishments", "relevant_files"
  ]
}
```

1. **Title**: A human-readable heading capturing the essence of the conversation.
2. **Keywords**: Relevant topics (1-3 tags).
3. **Summary**: Concise narrative capturing context and outcomes.
4. **Key Points**: Distilled takeaways ordered by importance.
5. **Key Facts**: Objective, verifiable facts extracted from the conversation.
6. **Action Items**: Concrete next steps or decisions with imperative phrasing.
7. **Goal**: The primary objective of the current session.
8. **Discoveries**: Technical findings, bug insights, or environmental observations.
9. **Accomplishments**: Tasks or milestones successfully completed.
10. **Relevant Files**: Paths to files that have been modified or are critical to the current task.
11. **Citations**: Source references supporting facts or quotes for traceability.
12. **Sentiment**: Overall tone of the content.
13. **Confidence**: Model-assessed confidence in the summary (0-10).
14. **Metadata**: Free-form auxiliary details (timestamp, processing notes, etc.).

### Compaction Prompts Customization

You can customize how summarization is performed by providing a `CompactionPrompts` object through the [Auto Compaction Setup](agents.md#auto-compaction-setup) at the agent level.

```java
final var customPrompts = CompactionPrompts.builder()
        .summarizationSystemPrompt("You are an expert at distilling chat history...")
        .summarizationUserPrompt("Please summarize these messages: ${sessionMessages}")
        .promptSchema(myJsonSchema) // (1)!
        .build();

final var autoCompactionSetup = AutoCompactionSetup.builder()
        .prompts(customPrompts)
        .tokenBudget(2000)
        .compactionTriggerThresholdPercentage(70)
        .build();
```

1. You can provide a custom JSON schema to change the structure of the generated summary.

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

### Emergency Compaction

If the model returns a `LENGTH_EXCEEDED` error, the session extension automatically triggers compaction to reduce the message history size, ensuring the next run can proceed with a summarized context.

## Notes

- The session extension handles message persistence and retrieval. For automatic threshold-based compaction to prevent context overflow, configure [Auto Compaction](agents.md#auto-compaction-setup) at the agent level.
- Manual compaction via `forceCompaction()` happens synchronously. For production use cases with long conversations, consider running it in a background task.
- Emergency compaction triggers automatically on `LENGTH_EXCEEDED` errors.

## Warnings

- **Concurrent Access**: While storage implementations like `ESSessionStore` are thread-safe, concurrent updates to the same session summary might lead to race conditions where one summary overwrites another.
- **Storage Growth**: Without a cleanup policy, session and message indices can grow significantly. Ensure you have a data retention strategy in place.

## Caution: Footguns

!!! failure "Manual Message Management"
    Avoid passing `oldMessages` in `AgentInput` while using the `AgentSessionExtension`. The extension automatically retrieves history from the store. Providing `oldMessages` manually can result in duplicate messages or incorrect message ordering in the model prompt, as `oldMessages` are injected *before* the system prompt and extension-managed history.

!!! failure "Changing Model Context Windows"
    If you change the model used by an agent to one with a significantly smaller context window, existing sessions might fail with `LENGTH_EXCEEDED` before emergency compaction can trigger. Configure [Auto Compaction](agents.md#auto-compaction-setup) with an appropriate threshold to prevent this issue.
