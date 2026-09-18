# Release Notes

## 1.2.0

- Added multimodal support. User prompts now accept images, audio, and file attachments (`MediaInput`).
- Optimized prompt cache hit rates. Dynamic data is now separated from the system prompt.
- Added deterministic ordering of tool names in extension system prompts.
- Message compactor now handles `GenericText` and `GenericResource` messages.
- Added per-user-message send time with configurable prompt time granularity.
- Fixed and enhanced the stream processing path.
- Improved `FileSystemAgentMemoryStorage` performance with pre-computed vector norms and score-first sort.
- Tool call arguments now default to `{}` when the model sends none.

## 1.1.1

- Added `sentinel-ai-examples` module with a text-to-SQL agent example.
- Added Lucene-based schema vector store with hybrid (keyword and semantic) search.
- Added an SSE-based MCP server for SQLite in the examples.
- Added `sentinel-ai-evals` module. Supports offline evals with embedding and LLM-as-a-judge.
- Added `sentinel-ai-instrumentation-otel` module for OpenTelemetry integration.
- Added `sentinel-ai-filesystem` module for agent file system operations.
- Added `sentinel-ai-reporting` module.
- Added agent skills extension.
- Added in-path and inline message compaction, with compaction events.
- Added a safe tool runner that blocks responses that are too large.
- Added tool retries and timeout support.
- Added OpenAI token counting and usage statistics in agent responses.
- Added input and error events.
- Added disk based message storage and a history store for all messages.
- Added MCP HTTP transport support and fixed the MCP tool runner.
- Added capability to register hand-crafted agents to the registry.
- Added per-agent model and model parameter configuration.
- Added LLM call timeout handling for streaming, with message preservation between retries.
- Fixed a SQL injection vulnerability and other security hotspots.

## 1.1.0

First public release.

- Agents with XML based system prompts and structured output.
- Sync and streaming model calls.
- Tool execution engine with approval callbacks.
- MCP integration through `sentinel-ai-toolbox-mcp`.
- Remote HTTP calls as tools through `sentinel-ai-toolbox-remote-http`.
- Agent memory and session extensions.
- Configured agents from JSON or YAML files.
- Handlebars template support for prompts.
- Output generation through tools for better reliability.
- Validation and retry support.
