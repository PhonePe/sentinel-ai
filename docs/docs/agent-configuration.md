---
title: Agent Configuration
description: Configuring compaction, retry and loop protection in Sentinel AI
---

# Agent Configuration

These settings are part of the `AgentSetup` builder. See [Agents](agents.md#agent-setup) for the full list of
available settings.

## Auto Compaction Setup

The `AutoCompactionSetup` class configures automatic message history compaction during agent execution. When enabled, Sentinel AI automatically compresses conversation history that exceeds a configured token threshold, allowing agents to maintain longer conversations without hitting context window limits.

| **Setting**                              | **Type**             | **Default** | **Description**                                                                                                                  |
|------------------------------------------|----------------------|-------------|----------------------------------------------------------------------------------------------------------------------------------|
| `prompts`                                | `CompactionPrompts`  | DEFAULT     | Custom prompts used for the compaction/summarization process.                                                                    |
| `tokenBudget`                            | `int`                | 1500        | Target token count for the compacted message history.                                                                            |
| `compactionTriggerThresholdPercentage`   | `int`                | 60          | Percentage of context window usage that triggers automatic compaction. Set to 0 to compact every run.                            |
| `model`                                  | `Model`              | null        | Optional separate model to use for compaction. If not provided, uses the agent's main model.                                     |

### How Auto Compaction Works

Auto compaction runs as a pre-processor before sending messages to the LLM:

1. **Token Estimation**: Estimates token count of messages generated after the last compaction point.
2. **Threshold Check**: Compares estimated tokens against the model's context window size.
3. **Trigger**: If `(estimatedTokens / contextWindowSize) * 100 > compactionTriggerThresholdPercentage`, triggers compaction.
4. **Compaction**: Invokes the `MessageCompactor` to summarize the history into a compact form.
5. **Continuation**: Appends a continuation prompt with the compacted summary, allowing the agent to proceed with context preserved.

### Example Configuration

```java
final var autoCompactionSetup = AutoCompactionSetup.builder()
        .tokenBudget(2000)
        .compactionTriggerThresholdPercentage(70)
        .prompts(CompactionPrompts.DEFAULT)
        .build();

final var agentSetup = AgentSetup.builder()
        .model(model)
        .modelSettings(modelSettings)
        .autoCompactionSetup(autoCompactionSetup)
        .build();
```

!!!tip "Token Budget Tuning"
    The `tokenBudget` determines how much of the conversation history is preserved in the compacted form. A larger budget preserves more detail but consumes more tokens. A smaller budget is more aggressive but may lose nuance. Start with the default (1500) and adjust based on your use case.

!!!note "Model Selection"
    You can optionally specify a different (typically smaller and faster) model for compaction tasks by setting the `model` field. This can reduce costs and latency for the compaction operation. If not set, the agent's main model is used.

## Retry Setup
The `RetrySetup` class is a configuration class that is used to configure the retry mechanism for model calls.

| **Setting**         | **Type**               | **Description**                                                                                 |
|---------------------|------------------------|-------------------------------------------------------------------------------------------------|
| `totalAttempts`   | `int`                  | Total number of attempts to make. This includes the successful attempts.                        |
| `delayAfterFailedAttempt` | `Duration`             | Delay after a failed attempt before retrying.                                                   |
| `retriableErrorTypes` | `Set<ErrorTypes>` | Specific error types to retry on. If not provided, a pre-defined set of error types are retried. See [Error Handling](errors.md) for details. |

## Tool Loop Protection Setup

The `ToolLoopProtectionSetup` class configures the protection against model runs that repeat
the same tool calls in a loop without progress. The protection works on model rounds. A
round is one model call and the tool calls the model requested in it. Tool calls inside one
round run in parallel, so a round is compared as a set: the order of the calls inside the
round does not matter.

| **Setting**            | **Type** | **Description**                                                                                                   |
|------------------------|----------|-------------------------------------------------------------------------------------------------------------------|
| `enabled`              | `boolean` | Master switch for the complete protection. Defaults to `true`.                                                    |
| `windowSize`           | `int`    | Number of rounds kept in the sliding window for repeat and cycle detection. Defaults to `20`.                     |
| `instructionThreshold` | `int`    | Number of repeats of an identical round that triggers an instruction to the model. Defaults to `3`.               |
| `terminationThreshold` | `int`   | Number of repeats of an identical round that terminates the run with `TOOL_LOOP_DETECTED`. Defaults to `5`.       |
| `maxToolRounds`        | `int`    | Maximum number of model rounds with tool calls in a run. Defaults to `25`. A value `<= 0` disables this cap.       |
| `maxToolCalls`         | `int`    | Maximum number of tool calls in a run. Defaults to `50`. A value `<= 0` disables this cap.                        |

The protection uses three layers:

1. **Round repeat detection:** if the same round (same set of tool calls with canonical
   arguments) repeats often enough inside the sliding window, the protection first sends an
   instruction to the model, then terminates the run if the repeat continues.
2. **Cycle detection:** if the sequence of rounds ends with a cycle of length 2 to 5 that
   repeats at least twice, for example A, B, A, B, the protection first sends an
   instruction to the model, then terminates the run if the cycle continues.
3. **Budgets:** the run is terminated when it exceeds the maximum number of tool rounds or
   tool calls. This backstop also catches loops with always-changing arguments.

The arguments are canonicalized before comparison: JSON objects are serialized with
recursively sorted keys, so two argument strings that differ only in key order or whitespace
produce the same key. Tools listed in `loopExemptTools` are exempt from repeat and cycle
detection. The run is terminated with `TOOL_CALL_BUDGET_EXCEEDED` when a budget is exceeded,
and with `TOOL_LOOP_DETECTED` when a loop is detected after the instruction was ignored. See
[Error Handling](errors.md) for details.
