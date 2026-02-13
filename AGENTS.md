# Agent Instructions for Sentinel AI

This document provides technical context and workflow requirements for AI agents contributing to the Sentinel AI project.

## Project Overview
Sentinel AI is a Java-based multi-module framework for building intelligent agents. It provides high-level abstractions for LLMs, tools, memory, and session management, enabling the creation of complex, stateful agents with minimal boilerplate.

## Project Structure
- `sentinel-ai-core`: Core agent logic, `Agent` base class, `Model` interface, and tool execution engine.
- `sentinel-ai-models-simple-openai`: Implementation for OpenAI models.
- `sentinel-ai-embedding`: Abstractions and implementations for embedding models.
- `sentinel-ai-agent-memory`: Extensible memory management (short-term/long-term).
- `sentinel-ai-session`: Session tracking and state persistence.
- `sentinel-ai-configured-agents`: Dynamic agent instantiation via JSON/YAML configurations.
- `sentinel-ai-toolbox-mcp`: Integration with Model Context Protocol (MCP).
- `sentinel-ai-toolbox-remote-http`: Capability for agents to execute remote HTTP calls as tools.
- `sentinel-ai-filesystem`: File system extension for agent operations.
- `sentinel-ai-bom`: Bill of Materials for version management.

## Core Concepts
### Agents
All agents extend `Agent<R, T, A>`.
- **R (Request)**: The input POJO.
- **T (Response)**: The output POJO (generated via structured output).
- **A (Agent)**: The agent type for fluent builder support.
- **System Prompts**: Structured as XML templates.

### Models & Tools
- **Model**: Interface for LLM interaction (supports sync, streaming, and text-only modes).
- **Tool**: Executable units of logic (`ExecutableTool`).
- **ToolBox**: A collection of tools that can be registered with an agent.

### Extensions
`AgentExtension` allows adding capabilities (like memory or custom domain logic) to agents modularly.

## Development Environment & Tooling

### Lombok & Build Process
The project relies heavily on **Lombok**. If you encounter "symbol not found" errors for getters, setters, or builders:
- Run `mvn clean install -DskipTests` from the root. This ensures that Lombok's annotation processing is triggered and all generated classes are available in the local repository.

### Code Formatting
We use **Spotless** for consistent code styling.
- **Mandatory:** Always run `mvn spotless:apply` before finalizing any code changes to ensure they adhere to the project's formatting rules.
- Styling is based on `java-format.xml` (Eclipse-style).

### Language Server (JDTLS)
If you are using `jdtls` or a similar Java LSP:
- Ensure the server is initialized at the **top-level directory** (the project root).
- The project is a multi-module Maven project; opening modules individually may lead to broken dependency resolution.

## Architecture Guidelines
- **Java 17**: Leverage modern Java features (records, sealed classes where appropriate).
- **Memory Dimensions:** Use `embeddingModel.dimensions()` instead of hardcoding vector sizes.
- **Type Safety:** Use `java.time.Duration` for timeouts and `java.time.LocalDateTime` for timestamps.
- **Internal Dependencies:** Always use the `sentinel-ai-bom` to manage versions across modules.

## Testing
- **Unit Tests**: Standard JUnit 5.
- **Real Tests**: Run with `-Preal-tests` to hit live LLM endpoints (requires `.env` file).
- **WireMock**: Used for mocking external service calls.

## Common Tasks for AI Agents
- **Implementing a Tool**: Create a class implementing `InternalTool` or expose methods in a `ToolBox`.
- **New Agent Implementation**: Extend `Agent`, define XML prompts, and specify IO types.
- **Adding Extensions**: Implement `AgentExtension` to hook into the agent's lifecycle.

## Best Practices for AI Interactions
- **Execution Flow**: Read `Agent.java`'s `executeAsync` method to understand the prompt -> model -> tool loop.
    - **Prompt Generation**: The system prompt is an XML structure.
    - **Model Call**: The agent sends the message history and tool definitions to the model.
    - **Tool Execution**: If the model requests a tool call, the `AgentToolRunner` executes it and adds the result to the history.
    - **Output Validation**: Once the model provides a final response, it is validated against the output schema.
- **Schema Validation**: Always validate JSON schemas for `ConfiguredAgent` inputs.
- **Error Handling**: Use the `SentinelError` class for consistent error reporting.
- **Documentation**: Always update the relevant documentation in the `docs/` directory once changes are implemented and verified by passing all tests.
