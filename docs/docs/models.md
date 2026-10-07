---
title: Models & Providers
description: Configuring LLM models and providers in Sentinel AI
---

# Models & Providers

## Instantiating a model

The `Model` class is a generic abstraction for an LLM model used by an agent. A concrete subclass of the Model needs to
be instantiated for usage in the agent.

Sentinel AI supports OpenAI API compliant model endpoints. The corresponding implementation of `Model` is the
`ConfiguredModel` class. The class is available in the `sentinel-ai-models` module.

The module needs to be added to the project dependencies as follows:

```xml

<dependency>
    <groupId>com.phonepe.sentinel-ai</groupId>
    <artifactId>sentinel-ai-models</artifactId>
</dependency>
```

The module is vendor-neutral. It uses OkHttp for HTTP transport and Jackson for JSON. No third-party LLM client SDK is
required. A model pairs a model name with a `Provider`. The `Provider` owns the endpoint, the wire protocol and the
authentication:

```java
final var model = ConfiguredModel.builder()
        .modelName("gpt-4o")
        .provider(Provider.builder()
                .baseUrl(EnvLoader.readEnv("OPENAI_ENDPOINT"))
                .protocol(new ChatCompletionsProtocol())
                .auth(HeaderAuth.bearer(EnvLoader.readEnv("OPENAI_API_KEY")))
                .build())
        .httpClient(httpClient)
        .build();
```

The final endpoint is the base URL plus the endpoint prefix plus the protocol path. The default prefix is `/v1`:
`https://api.openai.com` + `/v1` + `/chat/completions`.

Set `endpointPrefix` on the `Provider` when the endpoint does not mount the API under `/v1`. Use
`Provider.NO_ENDPOINT_PREFIX` (an empty string) for no prefix, for example `https://api.githubcopilot.com` +
`/responses`. Use a custom prefix for Azure style paths, for example
`/openai/deployments/{deployment}`.

!!!tip "Authentication"
    `HeaderAuth.bearer(apiKey)` sends the key as a `Bearer` token, and `HeaderAuth.of(header, value)` sets any other
    header pair. Set `auth` to null when your `OkHttpClient` handles authentication with its own interceptors (for
    example, in production environments with tightened security).

!!!note "Endpoint and api key"
    The `OPENAI_ENDPOINT` and `OPENAI_API_KEY` are environment variables that need to be set in the system. The
    `EnvLoader` class is a utility class that loads the environment variables. You can use any other method to load
    the environment variables as well.

## Model Settings

A variety of settings can be set for the model. The `ModelSettings` class is a configuration class that is used to
configure the model. The class is available in the core library itself and provides a builder.

| **Setting**         | **Type**               | **Description**                                                                                |
|---------------------|------------------------|------------------------------------------------------------------------------------------------|
| `maxTokens`         | `Integer`              | Maximum number of tokens to generate.                                                          |
| `temperature`       | `Float`                | Amount of randomness to inject in output. Lower values make the output more predictable.       |
| `topP`              | `Float`                | Probabilistic sum of tokens to consider for each subsequent token. Range: 0-1.                 |
| `timeout`           | `Duration`             | Timeout for model calls.                                                                       |
| `parallelToolCalls` | `Boolean`              | Whether to call tools in parallel or not.                                                      |
| `seed`              | `Integer`              | Seed for random number generator to make output more predictable.                              |
| `presencePenalty`   | `Float`                | Penalty for adding new tokens based on their presence in the output so far.                    |
| `frequencyPenalty`  | `Float`                | Penalty for adding new tokens based on how many times they have appeared in the output so far. |
| `logitBias`         | `Map<String, Integer>` | Controls the likelihood of specific tokens being generated.                                    |
| `disableTools`      | `Boolean`              | Disables tool calls for this agent. When `true`, tools are not sent to the model and `STRUCTURED_OUTPUT` mode is used. Useful for models that do not support tool calling. |

## Model Specific Options

Some models support additional configuration options that are not part of the standard `ModelSettings`. For example, models in the `sentinel-ai-models` module support `ModelOptions`.

### Token Counting Configuration

You can tune how Sentinel AI estimates token usage for OpenAI models by providing a `TokenCountingConfig`. This is useful for adjusting for specific prompt formats or model-specific overheads.

| **Setting**                 | **Type** | **Default** | **Description**                                                                                                       |
|-----------------------------|----------|-------------|----------------------------------------------------------------------------------------------------------------------|
| `messageOverHead`           | `int`    | 3           | Overhead tokens per message.                                                                                           |
| `nameOverhead`             | `int`    | 1           | Overhead tokens if `name` is provided in message.                                                                     |
| `assistantPrimingOverhead` | `int`    | 3           | Tokens added at the end of the prompt to prime assistant.                                                              |
| `formattingOverhead`       | `int`    | 10          | Overhead for structured tool arguments.                                                                               |
| `imageTokenCost`            | `int`    | 765         | Fixed token cost per image content part. Vision models do not tokenize the base64 payload as text, so each image part contributes this fixed cost instead. |

```java
final var tokenConfig = TokenCountingConfig.builder()
        .messageOverHead(3) // Overhead tokens per message
        .nameOverhead(1)    // Overhead tokens if 'name' is provided in message
        .assistantPrimingOverhead(3) // Tokens added at the end of the prompt to prime assistant
        .formattingOverhead(10) // Overhead for structured tool arguments
        .imageTokenCost(765) // Fixed token cost per image content part
        .build();

final var modelOptions = ModelOptions.builder()
        .tokenCountingConfig(tokenConfig)
        .toolChoice(ModelOptions.ToolChoice.AUTO)
        .build();

final var model = ConfiguredModel.builder()
        .modelName("gpt-4o")
        .provider(Provider.builder()
                .baseUrl(EnvLoader.readEnv("OPENAI_ENDPOINT"))
                .protocol(new ChatCompletionsProtocol())
                .auth(HeaderAuth.bearer(EnvLoader.readEnv("OPENAI_API_KEY")))
                .build())
        .modelOptions(modelOptions) // Pass options here
        .build();
```

The `toolChoice` field on `ModelOptions` controls the `tool_choice` parameter sent to the model. Sentinel AI
automatically resolves the effective OpenAI `tool_choice` value by combining `toolChoice` with the active
`outputGenerationMode`. The default in `TOOL_BASED` mode is `required`, but some models (e.g. Qwen, Kimi on vLLM)
do not call tools reliably in this configuration. Set `toolChoice` to `AUTO` for such models.

The resolution rules are:

| `outputGenerationMode` | `toolChoice`        | Effective OpenAI `tool_choice` | Notes                                                                                           |
|------------------------|---------------------|--------------------------------|-------------------------------------------------------------------------------------------------|
| `TOOL_BASED`           | `REQUIRED`          | `required`                     |                                                                                                 |
| `TOOL_BASED`           | `AUTO`              | `auto`                         |                                                                                                 |
| `TOOL_BASED`           | `DEFAULT`           | `required`                     | Default for tool-based mode — model must call a tool to produce output.                         |
| `STRUCTURED_OUTPUT`    | `REQUIRED`          | `required`                     | ⚠️ Warning logged: may cause infinite tool-call loops in structured-output mode.               |
| `STRUCTURED_OUTPUT`    | `AUTO`              | `auto`                         |                                                                                                 |
| `STRUCTURED_OUTPUT`    | `DEFAULT`           | `auto`                         | Default for structured-output mode — model chooses whether to call a tool.                      |

!!!warning "REQUIRED + STRUCTURED_OUTPUT"
    Setting `toolChoice` to `REQUIRED` while `outputGenerationMode` is `STRUCTURED_OUTPUT` is allowed but will emit a
    warning at runtime. This combination can cause the model to enter an infinite tool-call loop. Prefer `AUTO` or
    `DEFAULT` when using `STRUCTURED_OUTPUT`.

```java
// Use AUTO tool choice for models that ignore REQUIRED (e.g. Qwen, Kimi on vLLM)
final var modelOptions = ModelOptions.builder()
        .toolChoice(ModelOptions.ToolChoice.AUTO)
        .build();

final var model = ConfiguredModel.builder()
        .modelName("qwen-plus")
        .provider(Provider.builder()
                .baseUrl(EnvLoader.readEnv("OPENAI_ENDPOINT"))
                .protocol(new ChatCompletionsProtocol())
                .auth(HeaderAuth.bearer(EnvLoader.readEnv("OPENAI_API_KEY")))
                .build())
        .modelOptions(modelOptions)
        .build();
```

### Response Chaining

Models over the OpenAI Responses protocol support server-side conversation chaining via `ModelOptions.ResponseChaining`.
When enabled, each request references the previous stored response (`previous_response_id`) and sends only the new input
items; the provider stitches the conversation history server-side. This reduces request payload size and latency and
improves prompt cache hit rates, but it does **not** reduce billed input tokens: all chained history is billed as input
tokens by the provider.

| Mode     | Behavior                                                                                                   |
|----------|------------------------------------------------------------------------------------------------------------|
| `OFF`    | Default. Full history replay on every request; nothing is stored on the provider side.                      |
| `IN_RUN` | Chain only within one run's tool loop. Each run starts a fresh conversation.                                 |
| `SESSION`| Chain across runs in a session, anchored on the last stored response. Compaction always breaks the chain.    |

**Privacy note**: chaining requires the provider to store the generated responses (`store: true`); OpenAI retains stored
responses for at least 30 days. Keep chaining off if that is unacceptable. Compaction (automatic or manual) always
restarts the chain: the compacted summary plus the post-compaction history is sent as a fresh full request.

Chain-break errors (for example an expired or unknown stored response id) are surfaced to the caller as model call
errors; there is no automatic full-history retry yet.

```java
final var modelOptions = ModelOptions.builder()
        .responseChaining(ModelOptions.ResponseChaining.SESSION)
        .build();

final var model = ConfiguredModel.builder()
        .modelName("gpt-4o")
        .provider(Provider.builder()
                .baseUrl(EnvLoader.readEnv("OPENAI_ENDPOINT"))
                .protocol(new ResponsesProtocol())
                .auth(HeaderAuth.bearer(EnvLoader.readEnv("OPENAI_API_KEY")))
                .build())
        .modelOptions(modelOptions)
        .build();
```

### Model Call Retry

A model call retries on failure when a `RequestRetryPolicy` is set on the model. The default policy does not retry; one
attempt, identical to the engine behavior without a policy.

Retries trigger on network `IOException`s and on the configured HTTP status codes. Retries apply to the HTTP call only
and only before the first byte of the response body is consumed; a streaming response that already delivered events is
not retried. When a retried response carries a `Retry-After` header, the engine honors it over the computed backoff.

| Setting           | Type           | Default                  | Description                                                              |
|-------------------|----------------|--------------------------|--------------------------------------------------------------------------|
| `retryOnStatus`   | `Set<Integer>` | `429, 500, 502, 503, 504`| HTTP status codes that trigger a retry.                                  |
| `maxAttempts`     | `int`          | `1`                      | Maximum attempts including the first one. `1` or less means no retry.     |
| `initialDelay`    | `Duration`     | null                     | First backoff delay; must be set when more than one attempt is configured.|
| `maxDelay`        | `Duration`     | null                     | Upper bound of the backoff delay; null means no upper bound.             |
| `delayFactor`     | `double`       | `1.0`                    | Backoff multiplier after every attempt; `1.0` is a fixed delay.          |
| `honorRetryAfter`| `boolean`      | `true`                   | Honor the `Retry-After` header over the computed backoff when present.    |

```java
final var model = ConfiguredModel.builder()
        .modelName("gpt-4o")
        .provider(Provider.builder()
                .baseUrl(EnvLoader.readEnv("OPENAI_ENDPOINT"))
                .protocol(new ChatCompletionsProtocol())
                .auth(HeaderAuth.bearer(EnvLoader.readEnv("OPENAI_API_KEY")))
                .build())
        .requestRetryPolicy(RequestRetryPolicy.builder()
                .maxAttempts(3)
                .initialDelay(Duration.ofSeconds(1))
                .maxDelay(Duration.ofSeconds(10))
                .delayFactor(2.0)
                .build())
        .build();
```

### Wire Payload Logging

The model logs the JSON wire payload it exchanges with the provider. This eases protocol debugging. The level is a
`WireLoggingMode` on the model:

| Level    | Logs                                                                        |
|----------|-----------------------------------------------------------------------------|
| `ON`     | The request body, the final response and the body of every failed call.    |
| `FRAMES` | Everything `ON` logs, plus every raw stream frame; one log line per frame. |
| `OFF`    | Nothing.                                                                    |

The default is `ON`. A streaming response logs once, at the first finish event, as the assembled final response: finish
reason, content, tool calls and usage.

```java
final var model = ConfiguredModel.builder()
        .modelName("gpt-4o")
        .provider(Provider.builder()
                .baseUrl(EnvLoader.readEnv("OPENAI_ENDPOINT"))
                .protocol(new ChatCompletionsProtocol())
                .auth(HeaderAuth.bearer(EnvLoader.readEnv("OPENAI_API_KEY")))
                .build())
        .wireLogging(WireLoggingMode.FRAMES)
        .build();
```

The output goes to a dedicated logger, `com.phonepe.sentinelai.models.wire.WIRE`, at `INFO` level. Route or silence this
logger to control the wire payloads without touching other loggers.

!!!note "Environment overrides"
    The system property `sentinel.wire.logging` or the environment variable `SENTINEL_WIRE_LOGGING` overrides the
    configured mode. Valid values are `ON`, `FRAMES` and `OFF`, case-insensitive. An invalid value logs a warning and
    keeps the configured mode.

### Request Transformers

A `RequestTransformer` mutates the request body, the headers or the request after authentication and before
serialization. Declare transformers on the `Provider`, on the model, or per run through agent extensions; the model
applies them in that order. Built-in transformers cover Jolt body transforms, fixed extra headers and session id
injection for cache affinity. See [Request Transformers](request-transformers.md) for the full contract, the built-in
transformers and custom transformer examples.
