---
title: Request Transformers
description: Mutating outgoing model requests in Sentinel AI
---

# Request Transformers

A `RequestTransformer` mutates the outgoing model request after the protocol builds the body and the provider
authentication is applied, and before the request is serialized and sent. Transformers can mutate the request body (for
example vendor-specific payload fields), add headers, or adjust the request itself.

## When to use a transformer

Use a transformer when the request must change per call or per run and no protocol setting covers the case:

- Vendor-specific body fields with logic behind them, for example `chat_template_kwargs.thinking = false` on
  open-weight servers (Jolt)
- Gateway or proxy headers on every request (extra headers)
- Session or cache-affinity signals that providers read from a header or a body field (session id injection)

For declarative body fields that need no logic, `ModelOptions.extras` stays the simpler option; extras merge into the
request body last and win.

## Declaration levels and order

Transformers are declared at three levels:

| Level | Field | Typical use |
|---|---|---|
| Agent extension | `AgentExtension.requestTransformers(...)` | run-scoped tracing headers, per-run routing |
| Provider | `Provider.requestTransformers` | provider-wide headers, provider-level Jolt |
| Model | `ConfiguredModel.requestTransformers` | model-specific Jolt, model-specific headers |

The model applies them in the order **extension → provider → model**. Each transformer sees the output of the previous
one. Extension transformers are app-level; they must not see provider internals, and provider config must not leak into
agent code.

## The transformer context

Each transformer receives a `RequestTransformerContext` with:

- the wire context of the call (model name, base URL, mapper)
- the session id of this run — **empty when the run has no session id**; transformers must treat absence as a no-op
- the name of the agent that runs the model
- the agent messages of this turn
- the translated wire messages of this turn, the same list embedded in the request body

## Built-in transformers

### JoltRequestTransformer

`JoltRequestTransformer` applies a chain of [Jolt](https://github.com/bazaarvoice/jolt) operations to the request body.
Each `JoltTransform` pairs a Jolt operation name with its spec map; operations apply in list order. Load the transform
list from a typed list, a JSON string or a JSON node:

```java
final var model = ConfiguredModel.builder()
        .modelName("qwen3")
        .provider(Provider.builder()
                .baseUrl(EnvLoader.readEnv("OPENAI_ENDPOINT"))
                .protocol(new ChatCompletionsProtocol())
                .auth(HeaderAuth.bearer(EnvLoader.readEnv("OPENAI_API_KEY")))
                .requestTransformer(JoltRequestTransformer.fromJson("""
                        [
                          {"operation": "default", "spec": {"chat_template_kwargs": {"thinking": false}}}
                        ]"""))
                .build())
        .build();
```

Invalid Jolt operation names fail at transformer construction. A non-object transform result fails the model call.

### ExtraHeadersRequestTransformer

`ExtraHeadersRequestTransformer` adds fixed headers to every request. The body and the context are ignored. Declare it
on the `Provider` for provider-wide headers such as gateway or proxy headers, or on the model for model-specific
headers:

```java
final var model = ConfiguredModel.builder()
        .modelName("gpt-4o")
        .provider(Provider.builder()
                .baseUrl(EnvLoader.readEnv("OPENAI_ENDPOINT"))
                .protocol(new ChatCompletionsProtocol())
                .auth(HeaderAuth.bearer(EnvLoader.readEnv("OPENAI_API_KEY")))
                .requestTransformers(List.of(ExtraHeadersRequestTransformer.builder()
                        .header("x-gateway-tenant", "acme")
                        .header("x-api-version", "2")
                        .build()))
                .build())
        .build();
```

### SessionIdInjectionTransformer

`SessionIdInjectionTransformer` sends the session id of the current run to the provider as a cache-affinity signal.
Configure the mechanism the provider supports: a header name, a JSON pointer to a body location, or both. The pointer
creates missing intermediate objects; `/session_id` addresses a top-level field, `/metadata/session_id` a nested one.
When the run has no session id or no mechanism is configured, the transformer adds nothing.

```java
final var model = ConfiguredModel.builder()
        .modelName("gpt-4o")
        .provider(Provider.builder()
                .baseUrl(EnvLoader.readEnv("OPENROUTER_ENDPOINT"))
                .protocol(new ChatCompletionsProtocol())
                .auth(HeaderAuth.bearer(EnvLoader.readEnv("OPENROUTER_API_KEY")))
                .requestTransformers(List.of(SessionIdInjectionTransformer.builder()
                        .header("x-session-id")
                        .bodyPath("/session_id")
                        .build()))
                .build())
        .build();
```

Known provider mechanisms: OpenRouter uses header `x-session-id` or body pointer `/session_id`; the OpenAI API uses
body pointer `/prompt_cache_key`; Fireworks AI uses header `x-session-affinity`; Anthropic-compatible gateways use
header `X-Session-Id`.

## Failure semantics

A transformer that throws aborts the model call. The failure is reported as a `REQUEST_TRANSFORM_FAILED` error; there
is no silent fallback to the untransformed request. `SessionIdInjectionTransformer` additionally validates its body
pointer: the pointer must start with `/` and must not pass through a non-object node; both violations fail the call
the same way.

## Writing a custom transformer

Implement the `RequestTransformer` interface:

```java
public final class TraceIdHeaderTransformer implements RequestTransformer {

    @Override
    public void transform(final Request.Builder requestBuilder,
                          final ObjectNode body,
                          final RequestTransformerContext ctx) {
        ctx.sessionId().ifPresent(sessionId
                -> requestBuilder.header("x-trace-session", sessionId));
    }
}
```

Declare the custom transformer at any of the three levels, the same as the built-ins. A custom transformer must treat
an absent session id as a no-op.
