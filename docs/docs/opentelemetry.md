---
title: OpenTelemetry Instrumentation
description: Tracing Sentinel AI agents with OpenTelemetry GenAI semantic conventions
---

# OpenTelemetry Instrumentation

Sentinel AI provides OpenTelemetry support through `OpenTelemetryAgentExtension` in the `sentinel-instrumentation` module.

## Dependency

```xml
<dependency>
    <groupId>com.phonepe.sentinel-ai</groupId>
    <artifactId>sentinel-instrumentation</artifactId>
</dependency>
```

## What gets emitted

The extension emits spans that align with the GenAI semantic conventions:

- `invoke_agent {gen_ai.agent.name}` (`SpanKind.INTERNAL`)
- `execute_tool {gen_ai.tool.name}` (`SpanKind.INTERNAL`)

The extension subscribes to the existing Sentinel event bus and does not require changes in your tools, model, or session storage.

## Configure and register

```java
import com.phonepe.sentinelai.instrumentation.otel.OpenTelemetryAgentExtension;
import com.phonepe.sentinelai.instrumentation.otel.OpenTelemetryAgentExtensionSetup;
import io.opentelemetry.api.GlobalOpenTelemetry;

final var tracingExtension = OpenTelemetryAgentExtension
        .<MyRequest, MyResponse, MyAgent>builder()
        .setup(OpenTelemetryAgentExtensionSetup.builder()
                       .tracer(GlobalOpenTelemetry.getTracer("sentinel-ai"))
                       .providerName("openai")
                       .captureToolCallArguments(false)
                       .captureToolCallResult(false)
                       .build())
        .build();

final var agent = new MyAgent(agentSetup, List.of(tracingExtension), Map.of());
```

## Attributes

By default, spans include:

- `gen_ai.operation.name`
- `gen_ai.provider.name`
- `gen_ai.agent.name`
- `gen_ai.conversation.id` (if session id is present)
- `gen_ai.tool.name`
- `gen_ai.tool.call.id`
- `gen_ai.usage.input_tokens`
- `gen_ai.usage.output_tokens`
- `error.type` (on errors)

Optional attributes:

- `gen_ai.tool.call.arguments` (`captureToolCallArguments=true`)
- `gen_ai.tool.call.result` (`captureToolCallResult=true`, only for successful tool calls)

## Notes

- Tool argument/result capture may include sensitive data. Keep it disabled unless needed.
- The extension is event-driven and works with both streaming and non-streaming execution paths.
- Session storage (`SessionStore`) remains independent; only `sessionId` is used for trace correlation.

