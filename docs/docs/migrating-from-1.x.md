---
title: Migrating from 1.x to 2.x
description: What changed in Sentinel AI 2.0 and how to migrate your code
---

# Migrating from 1.x to 2.x

This guide covers the breaking changes in Sentinel AI 2.0 and shows how to move your 1.x code to 2.x.

## Overview

The biggest change in 2.0 is the model layer. The `sentinel-ai-models-simple-openai` module is replaced by the new
`sentinel-ai-models` module. The new module speaks the OpenAI wire protocols (Chat Completions and Responses) natively
over OkHttp and Jackson. No third-party LLM client SDK is used.

| | 1.x | 2.x |
|---|---|---|
| Module | `sentinel-ai-models-simple-openai` | `sentinel-ai-models` |
| Model class | `SimpleOpenAIModel` | `ConfiguredModel` |
| Transport | `simple-openai` SDK | Native OkHttp + Jackson |
| Auth | SDK `apiKey` builder method | `HeaderAuth.bearer(...)` or `HeaderAuth.of(...)` on the `Provider` |
| Model options | `SimpleOpenAIModelOptions` | `ModelOptions` |

The rest of the framework (agents, tools, extensions, memory, sessions) keeps its API. Most projects only need to change
the model construction code and the Maven coordinates.

## Update dependencies

Replace the old module in your `pom.xml`:

```xml
<!-- 1.x -->
<dependency>
    <groupId>com.phonepe.sentinel-ai</groupId>
    <artifactId>sentinel-ai-models-simple-openai</artifactId>
</dependency>

<!-- 2.x -->
<dependency>
    <groupId>com.phonepe.sentinel-ai</groupId>
    <artifactId>sentinel-ai-models</artifactId>
</dependency>
```

If you use the BOM, both entries are managed; remove the old one.

## Migrate the model

### 1.x construction

```java
final var model = new SimpleOpenAIModel<>(
        "gpt-4o",
        SimpleOpenAI.builder()
                .baseUrl(EnvLoader.readEnv("OPENAI_ENDPOINT"))
                .apiKey(EnvLoader.readEnv("OPENAI_API_KEY"))
                .objectMapper(objectMapper)
                .clientAdapter(new OkHttpClientAdapter(httpClient))
                .build(),
        objectMapper
);
```

### 2.x construction

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

!!!note "Endpoint prefix"
    The final endpoint is the base URL plus the endpoint prefix plus the protocol path. The default prefix is `/v1`:
    `https://api.openai.com` + `/v1` + `/chat/completions`. If your old endpoint URL included `/v1` at the end, the
    behavior stays the same. For endpoints that do not mount the API under `/v1`, set `endpointPrefix` on the
    `Provider`: `Provider.NO_ENDPOINT_PREFIX` for none, or a custom prefix such as the Azure
    `/openai/deployments/{deployment}` path.

!!!note "Authentication"
    `HeaderAuth.bearer(apiKey)` sends the key as a `Bearer` token. Use `HeaderAuth.of(header, value)` for any other
    header pair. Set `auth` to `null` when your `OkHttpClient` handles authentication with its own interceptors.

### Responses protocol

The new module also supports the OpenAI Responses protocol. Use `new ResponsesProtocol()` instead of
`new ChatCompletionsProtocol()` on the `Provider`. The 1.x module supported Chat Completions only.

## Migrate model options

Rename `SimpleOpenAIModelOptions` to `ModelOptions`. The builder methods and the `ToolChoice` values (`REQUIRED`,
`AUTO`, `DEFAULT`) keep their names:

```java
final var modelOptions = ModelOptions.builder()
        .tokenCountingConfig(tokenConfig)
        .toolChoice(ModelOptions.ToolChoice.AUTO)
        .build();

final var model = ConfiguredModel.builder()
        .modelName("gpt-4o")
        .provider(...)
        .modelOptions(modelOptions)
        .build();
```

## Migrate configured agents

If you use `sentinel-ai-configured-agents`, replace `SimpleOpenAIModelFactory` with `ConfiguredModelFactory`. The new
factory supports `ConfiguredModel` as the default model. The model name from the agent configuration overrides the
default model name; every other setting is inherited from the default model.

## Migrate evals

`sentinel-ai-evals` users implement `LLMModelFactory` to wire a model into the metric executors. Change the
implementation to build a `ConfiguredModel`:

```java
LLMModelFactory factory = identifier -> ConfiguredModel.builder()
        .modelName(identifier.modelId())
        .provider(Provider.builder()
                .baseUrl(baseUrl)
                .protocol(new ChatCompletionsProtocol())
                .auth(HeaderAuth.bearer(apiKey))
                .build())
        .build();
```

## New capabilities in 2.x

These features are new in 2.0. They are optional; existing behavior stays the same until you enable them.

- **Request transformers**: mutate the request body, headers or request per provider, per model or per run. See
  [Request Transformers](request-transformers.md).
- **`ModelOptions.extras`**: free-form JSON node merged into the request body last (extras win) for open-weight server
  specific fields.
- **`RequestRetryPolicy`**: Failsafe based retry for model calls on `IOException` and HTTP 429/500/502/503/504, with
  `Retry-After` support. The default policy does not retry.
- **Wire payload logging**: log request and response JSON to the `com.phonepe.sentinelai.models.wire.WIRE` logger.
  Controlled by `WireLoggingMode` (`ON`, `FRAMES`, `OFF`).
- **Tool loop protection**: detects and breaks repeated tool call loops. See [Tools](tools.md).
- **Pending messages queue**: inject mid-run messages into an agent run.

## Getting help

If you hit a migration issue not covered here, open a discussion or an issue on
[GitHub](https://github.com/PhonePe/sentinel-ai).
