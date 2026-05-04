# Sentinel Instrumentation

This module contains observability extensions for Sentinel AI.

## OpenTelemetry Agent Extension

`OpenTelemetryAgentExtension` emits GenAI semantic-convention spans using Sentinel's event bus:

- `invoke_agent {agent_name}` (`gen_ai.operation.name=invoke_agent`)
- `execute_tool {tool_name}` (`gen_ai.operation.name=execute_tool`)

### Usage

```java
import com.phonepe.sentinelai.instrumentation.otel.OpenTelemetryAgentExtension;
import com.phonepe.sentinelai.instrumentation.otel.OpenTelemetryAgentExtensionSetup;
import io.opentelemetry.api.GlobalOpenTelemetry;

final var otelExtension = OpenTelemetryAgentExtension
        .<MyRequest, MyResponse, MyAgent>builder()
        .setup(OpenTelemetryAgentExtensionSetup.builder()
                       .tracer(GlobalOpenTelemetry.getTracer("sentinel-ai"))
                       .providerName("openai")
                       .captureToolCallArguments(false)
                       .captureToolCallResult(false)
                       .build())
        .build();

final var agent = new MyAgent(agentSetup, List.of(otelExtension), Map.of());
```

### Run tests

```bash
mvn test -pl sentinel-instrumentation
```

