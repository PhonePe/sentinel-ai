---
title: Agents
description: Creating and using agents in Sentinel AI
---

# Agent Basics

The core abstraction of Sentinel AI is the `Agent` class. The `Agent` class is a generic class that takes three type
parameters:

- `R`: The type of the request object that the agent receives. This can be a string or any other complex Java type.
- `T`: The type of the response object that the agent returns. This can also be a string or any other complex Java type.
- `A`: The agent subtype. This is the class you are currently implementing.

## Request and Response Type parameters

Sentinel supports sending both text and objects as input and output. The type parameters `R` and `T` can be any Java
type, including strings, lists, maps, or custom objects. The only requirement is that the types must be serializable to
JSON.

Sentinel will generate schema for the type parameters and pass is to the model to ensure requests are interpreted
correctly and responses are generated properly.

!!!tip "Use `@JsonClassDescription` and `@JsonPropertyDescription` liberally"
    Use the `@JsonClassDescription` and `@JsonPropertyDescription` annotations to provide copious amounts of
    documentation on the classes and their members wherever they are used, be it as request type, response type, tool
    parameters and so on. This is added to the generated schema. A lot of the accuracy of the agent will finally depend
    on the amount of information you provide to the model. The more information you provide, the better the model will
    be able to interpret the request properly and generate the correct and relevant response.

Sample request type would be like the following:

```java title="BookInfo.java"

@JsonClassDescription("Information about the book to be summarized")
public record BookInfo(
        @JsonPropertyDescription("Unique ID for the book") String isbn,
        @JsonPropertyDescription("Title of the book") String title
) {
}
```

Similarly, the response type can be a complex object as well. For example, if you are implementing a book summarizer, a
sample response can be like the following:

```java title="BookSummary.java"

@JsonClassDescription("Summary of the book")
public record BookSummary(
        @JsonPropertyDescription("Unique ID for the book") String isbn,
        @JsonPropertyDescription("Summary of the book") String summary,
        @JsonPropertyDescription("Topics discussed in the book") List<String> topics
) {
}
```

## Instantiating a model

The `Model` class is a generic abstraction for an LLM model used by an agent. A concrete subclass of the Model needs to
be instantiated for usage in the agent.

Currently, we support _only_ OpenAI API compliant model endpoints. The corresponding implementation of `Model` for this
is the `SimpleOpenAIModel` class. The class is available in the `sentinel-ai-models-simple-openai` module.

The module needs to be added to the project dependencies as follows:

```xml

<dependency>
    <groupId>com.phonepe.sentinel-ai</groupId>
    <artifactId>sentinel-ai-models-simple-openai</artifactId>
</dependency>
```

This will add the required dependencies to instantiate the model with the SimpleOpenAI client library. The library
itself is very flexible, and you should read the documentation for the library to understand how to use it.
The model can be instantiated as follows:

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

!!!tip "Type parameter for `SimpleOpenAIModel`"
    The `SimpleOpenAIModel` is a generic class. The type is inferred from the type of the model. Leave it as `<>`.

!!!note "Endpoint and api key"
    The `OPENAI_ENDPOINT` and `OPENAI_API_KEY` are environment variables that need to be set in the system. The
    `EnvLoader` class is a utility class that loads the environment variables. You can use any other method to load
    the environment variables as well.

## Agent Setup

The `AgentSetup` class is a configuration class that is used to configure the agent. The class is available in the
core library itself and can be used to set a variety of settings for the agent. The class provides a builder to allow to
set only the required parameters and will default whatever it can if not provided.

> **AgentSetup** object needs to be passed at startup. However, if all parameters are not known or some of them need to
> be
> dynamic, a setup object can be passed as parameter to the `execute*` methods as well.

### Available Settings

Here are all available settings for the `AgentSetup` class:

| **Setting**            | **Type**               | **Description**                                                                                                   |
|------------------------|------------------- -----|-------------------------------------------------------------------------------------------------------------------|
| `mapper`               | `ObjectMapper`          | The object mapper to use for serialization/deserialization. If not provided, a default one will be created.       |
| `model`                | `Model`                 | The LLM to be used for the agent. This can be provided at runtime. If not provided, an error will be thrown.      |
| `modelSettings`        | [`ModelSettings`](#model-settings)  | The settings for the model. This can be provided at runtime. If not provided, an error will be thrown.            |
| `executorService`      | `ExecutorService`       | The executor service to use for running the agent. If not provided, a default cached thread pool will be created. |
| `eventBus`             | `EventBus`              | The event bus to be used for the agent. If not provided, a default event bus will be created.                     |
| `outputGenerationMode` | `OutputGenerationMode`  | Output generation mode to use for this model. Can be `TOOL_BASED` (default) or `STRUCTURED_OUTPUT`. Typically, other than OpenAI models, it is safer to leave it at the default `TOOL_BASED` mode. |
| `outputGenerationTool` | `UnaryOperator<String>` | A function that the model can use to generate the JSON string output. If not provided (recommended), Sentinel AI will use it's built in tool if the `outputGenerationMode` is set to `TOOL_BASED`  |
| `retrySetup`           | [`RetrySetup`](#retry-setup)            | Retry setup to use for model calls. If not provided, default setup will be added.                                |

!!!danger "Required parameters"
    - The `model`, and `modelSettings` are required parameters. If not provided, an error will be thrown. However, it is
      possible that model etc is not known during agent creation. In that case, the parameters can be provided as part of
      the `execute*` methods. If neither is available, exception will be provided at runtime.
    - All the other parameters are optional. If not provided, a default one will be created/provided.

### Model Settings

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

### Model Specific Options

Some models support additional configuration options that are not part of the standard `ModelSettings`. For example, `SimpleOpenAIModel` supports `SimpleOpenAIModelOptions`.

#### Token Counting Configuration

You can tune how Sentinel AI estimates token usage for OpenAI models by providing a `TokenCountingConfig`. This is useful for adjusting for specific prompt formats or model-specific overheads.

```java
final var tokenConfig = TokenCountingConfig.builder()
        .messageOverHead(3) // Overhead tokens per message
        .nameOverhead(1)    // Overhead tokens if 'name' is provided in message
        .assistantPrimingOverhead(3) // Tokens added at the end of the prompt to prime assistant
        .formattingOverhead(10) // Overhead for structured tool arguments
        .build();

final var modelOptions = SimpleOpenAIModelOptions.builder()
        .tokenCountingConfig(tokenConfig)
        .toolChoice(SimpleOpenAIModelOptions.ToolChoice.AUTO)
        .build();

final var model = new SimpleOpenAIModel<>(
        "gpt-4o",
        client,
        objectMapper,
        modelOptions // Pass options here
);
```

### Retry Setup
The `RetrySetup` class is a configuration class that is used to configure the retry mechanism for model calls.

| **Setting**         | **Type**               | **Description**                                                                                 |
|---------------------|------------------------|-------------------------------------------------------------------------------------------------|
| `totalAttempts`   | `int`                  | Total number of attempts to make. This includes the successful attempts.                        |
| `delayAfterFailedAttempt` | `Duration`             | Delay after a failed attempt before retrying.                                                   |
| `retriableErrorTypes` | `Set<ErrorTypes>` | Specific error types to retry on. If not provided, pre-defined set of error types are retried.  Check [relevant section](#default-error-codes). |
                                    

#### Default Error Codes
The following error codes are retried by default:

| Error Code                    | Description                                   | Retriable |
|-------------------------------|-----------------------------------------------|-----------|
| SUCCESS                       | Success                                       | No        |
| NO_RESPONSE                   | No response                                   | Yes       |
| REFUSED                       | Refused                                       | No        |
| FILTERED                      | Content filtered                              | No        |
| LENGTH_EXCEEDED               | Content length exceeded                       | No        |
| TOOL_CALL_PERMANENT_FAILURE   | Tool call failed permanently for tool         | No        |
| TOOL_CALL_TEMPORARY_FAILURE   | Tool call failed temporarily for tool         | Yes       |
| JSON_ERROR                    | Error parsing JSON                            | Yes       |
| SERIALIZATION_ERROR           | Error serializing object to JSON              | Yes       |
| DESERIALIZATION_ERROR         | Error deserializing object to JSON            | Yes       |
| UNKNOWN_FINISH_REASON         | Unknown finish reason                         | Yes       |
| GENERIC_MODEL_CALL_FAILURE    | Model call failed with error                  | Yes       |
| DATA_VALIDATION_FAILURE       | Model data validation failed. Errors          | Yes       |
| FORCED_RETRY                  | Retry has been forced                         | Yes       |
| UNKNOWN                       | Unknown response                              | Yes       |

!!!warning
    Refer to [ErrorCode.java](https://github.com/PhonePe/sentinel-ai/blob/master/sentinel-ai-core/src/main/java/com/phonepe/sentinelai/core/errors/ErrorType.java){:target="_blank"} to get the latest list of error codes.

### Sample setup

Sample code for creating settings for an agent:

```java
final var agentSetup = AgentSetup.builder()
        .model(model)
        .mapper(objectMapper)
        .modelSettings(ModelSettings.builder()
                               .temperature(0.1f)
                               .timeout(Duration.ofSeconds(10))
                               .seed(1)
                               .build())
        .build();
```

## Creating an agent

To create an agent, you need to do the following:

- Implement the `Agent` interface with the appropriate request and response type parameters
- Provide a system prompt
- Pass a setup object to the agent
- There are other parameters we shall explore in subsequent sections

Continuing with the example, we want to create an agent that can summarize books. Code for such an agent would look
something like this:

```java title="BookSummarizingAgent.java"

public class BookSummarizingAgent extends Agent<BookInfo, BookSummary, BookSummarizingAgent> {
    public BookSummarizingAgent(AgentSetup setup) {
        super(BookSummary.class,
              "You are an expert in summarizing books. You will be provided with the title and ISBN of a book." +
                      " You need to summarize the book and provide the topics discussed in the book.",
              setup,
              List.of(),
              Map.of());
    }

    @Override
    public String name() {
        return "book-summarizer";
    }
}

```

## The `AgentInput` class

Sentinel agent `execute*` requests can take multiple parameters along with the core request(user prompt). The
`AgentInput` class wraps all parameters and provides a builder allowing users to easily send or skip additional
parameters.

| **Property**      | **Type**               | **Description**                                                                                                                                               |
|-------------------|------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `request`         | `R`                    | Request object. This is a required parameter.                                                                                                                 |
| `facts`           | `List<FactList>`       | List of facts to be passed to the agent. This is passed to LLM as 'knowledge' in the system prompt.                                                           |
| `requestMetadata` | `AgentRequestMetadata` | Metadata for the request.                                                                                                                                     |
| `oldMessages`     | `List<AgentMessage>`   | List of old messages to be sent to the LLM for this run. If set to `null`, messages are generated and consumed by the agent in this session.                  |
| `agentSetup`      | `AgentSetup`           | Setup for the agent. Overrides runtime setup. If set to `null`, the setup provided during agent creation is used. Fields provided at runtime take precedence. |

## The `AgentOutput` class

The return type for all `execute*` methods is `AgentOutput` which is a generic class typed with the
response type `T`. The class contains the following fields:

| **Member**    | **Type**             | **Description**                                                                       |
|---------------|----------------------|---------------------------------------------------------------------------------------|
| `data`        | `T`                  | The output of the agent, typed to the required response type. Null in case of errors. |
| `newMessages` | `List<AgentMessage>` | New messages generated by the agent. Empty in case of errors.                         |
| `allMessages` | `List<AgentMessage>` | All messages generated by the agent, including the new messages.                      |
| `usage`       | `ModelUsageStats`    | Usage statistics for the model.                                                       |
| `error`       | `SentinelError`      | Error in case of failure or a success object otherwise.                               |

### Model Usage Statistics

The `ModelUsageStats` class tracks usage statistics for a model, including token usage and request details.

| **Member**             | **Type**               | **Description**                                                                                                         |
|------------------------|------------------------|-------------------------------------------------------------------------------------------------------------------------|
| `requestsForRun`       | `int`                  | Number of requests made for this run.                                                                                   |
| `toolCallsForRun`      | `int`                  | Number of tool calls made for this run.                                                                                 |
| `requestTokens`        | `int`                  | Number of request/prompt tokens used in this run. Equivalent to the "prompt_tokens" parameter in OpenAI usage.          |
| `responseTokens`       | `int`                  | Number of completion/response tokens used in this run. Equivalent to the "completion_tokens" parameter in OpenAI usage. |
| `totalTokens`          | `int`                  | Total tokens used in the whole run. Should generally equal `requestTokens + responseTokens`.                            |
| `requestTokenDetails`  | `PromptTokenDetails`   | Token usage details for prompts.                                                                                        |
| `responseTokenDetails` | `ResponseTokenDetails` | Token usage details for responses.                                                                                      |
| `details`              | `Map<String, Integer>` | Additional details about token usage.                                                                                   |

#### `PromptTokenDetails` Class

The `PromptTokenDetails` class provides detailed information about tokens used in prompts.

| **Member**     | **Type** | **Description**                                     |
|----------------|----------|-----------------------------------------------------|
| `cachedTokens` | `int`    | Number of cached tokens present in the prompt.      |
| `audioTokens`  | `int`    | Number of audio input tokens present in the prompt. |

#### `ResponseTokenDetails` Class

The `ResponseTokenDetails` class provides detailed information about tokens used in responses.

| **Member**                 | **Type** | **Description**                                                                                        |
|----------------------------|----------|--------------------------------------------------------------------------------------------------------|
| `reasoningTokens`          | `int`    | Number of tokens generated by the model for reasoning.                                                 |
| `acceptedPredictionTokens` | `int`    | Number of tokens in the prediction that appeared in the completion when using predicted outputs.       |
| `rejectedPredictionTokens` | `int`    | Number of tokens in the prediction that did not appear in the completion when using predicted outputs. |
| `audioTokens`              | `int`    | Number of audio input tokens generated by the model.                                                   |

## Using the agent

The agent can be invoked by calling any of the provided `execute*` methods.

- `executeAsync()` method and it's overloads can be used to (you guessed it) invoke the LLM asynchronously. It returns a
  `CompletableFuture` object which can be used to get the result when it is available.
- `execute()` method and it's overloads can be used to invoke the LLM synchronously. It returns the result directly.

In either case, the agent will be invoked with the provided request object and the response object will be returned
along with errors and usage information.

```java
final var agent = new BookSummarizingAgent(agentSetup);

final var response = agent.execute(
        AgentInput.<BookInfo>builder()
                .request(new BookInfo("978-0393096729", "War and Peace"))
                .build());
System.out.println(objectMapper.writerWithDefaultPrettyPrinter()
                               .writeValueAsString(response.getData()));

```

Output from the above would be something like:

```text
{
  "isbn" : "978-0393096729",
  "summary" : "\"War and Peace\" is a historical novel by Leo Tolstoy that intertwines the lives of several families during the Napoleonic Wars in the early 19th century. The narrative explores themes of love, fate, and the impact of war on society. It follows characters such as Pierre Bezukhov, Prince Andrei Bolkonsky, and Natasha Rostova as they navigate personal struggles and the broader historical events that shape their lives. The novel delves into the philosophical questions of history and the nature of power, ultimately portraying the complexity of human experience amidst the chaos of war.",
  "topics" : [ "Historical fiction", "Napoleonic Wars", "Russian society", "Philosophy of history", "Love and relationships", "Fate and free will", "Family dynamics", "War and its consequences" ]
}
```

## Request Metadata

Sometimes it is important to maintain context of the conversation. For example, if you are building a chat agent, you
may want to keep track of the session or the user the conversation is happening with. SentinelAI provides the
`AgentRequestMetadata` class for this purpose. The metadata class also provides the option to send back the
`ModelUsageStats` object from previous calls. This can be used to keep track of the usage of the model and the agent
across calls. If provided, the agent will merge the usage from current execution to the provided usage stats object.

!!!note "Request metadata passed to model"
    The request metadata passed to execute calls are serialized and passed to the LLM as part of the structured system
    prompt.

| **Property**   | **Type**              | **Description**                                                                                                                                                         |
|----------------|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sessionId`    | `String`              | Session ID for the current conversation. This is passed to LLM as additional data in the system prompt.                                                                 |
| `userId`       | `String`              | A User ID for the user the agent is having the current conversation with. This is passed to LLM as additional data in the system prompt.                                |
| `customParams` | `Map<String, Object>` | Any other custom parameters that need to be passed to the agent or the tools being invoked by the agent. This is passed to LLM as additional data in the system prompt. |
| `usageStats`   | `ModelUsageStats`     | Global usage stats object that can be used to track usage of the model across execute calls.                                                                            |

!!!note
    Request metadata is optional and passing `null` for this param is acceptable.

## Prompts

Sentinel AI has some special handling to improve LLM performance for agentic use cases both for system and user prompts.

### System Prompts

SentinelAI converts the system prompt in an XML format for easy parsing by the LLM. The string or object passed as the
system prompt to the agent is passed in a tag called `<role>`. Other information such as tools, properties from request
metadata as well as facts and additional tasks from the registered extensions are added to the prompt as well.

!!!danger "Serializability requirements"
    The system prompt needs to be serializable to XML. If not, an error will be thrown.

!!!tip "Structured prompts"
    We recommend passing the system prompt as a structured object. This will help the LLM understand the context better
    and speed up processing as well. Check the `SystemPrompt` class for tips on how to use different jackson annotations
    to make system prompts serialize correctly.

```xml title="Sample system prompt generated by SentinelAI"
<?xml version='1.1' encoding='UTF-8'?>
<SystemPrompt>
    <coreInstructions>Your main job is to answer the user query as provided in user prompt in the `user_input` tag.
        Perform the provided secondary tasks as well and populate the output in designated output field for the task.
        Use the provided knowledge and facts to enrich your responses and avoid unnecessary tool calls.
    </coreInstructions>
    <primaryTask>
        <role>greet the user</role> <!--(1)!-->
        <tools> <!--(2)!-->
            <tool>
                <name>test_tool_box_get_location_for_user</name>
                <description>Get location for user</description>
            </tool>
            <tool>
                <name>simple_agent_get_name</name>
                <description>Get name of user</description>
            </tool>
            <tool>
                <name>test_tool_box_get_weather_today</name>
                <description>Get weather today</description>
            </tool>
            <tool>
                <name>simple_agent_get_salutation</name>
                <description>Get salutation for user</description>
            </tool>
        </tools>
    </primaryTask>
    <secondaryTasks> <!--(3)!-->
        <secondaryTask>
            <instructions>
                <tasks>
                    <task>
                        <objective>EXTRACT MEMORY FROM MESSAGES AND POPULATE `memoryOutput` FIELD</objective>
                        <outputField>memoryOutput</outputField>
                        <instructions>How to extract different memory types:
                            - SEMANTIC: Extract fact about the session or user or any other subject
                            - EPISODIC: Extract a specific event or episode from the conversation
                            - PROCEDURAL: Extract a procedure as a list of steps or a sequence of actions that you can
                            use later
                        </instructions>
                        <additionalInstructions>IMPORTANT INSTRUCTION FOR MEMORY EXTRACTION:
                            - Do not include non-reusable information as memories.
                            - Extract as many useful memories as possible
                        </additionalInstructions>
                        <tools>
                            <tool>
                                <name>agent_memory_extension_find_procedural_memory</name>
                                <description>Find procedural memory about any topic from the store</description>
                            </tool>
                        </tools>
                    </task>
                </tasks>
            </instructions>
        </secondaryTask>
    </secondaryTasks>
    <additionalData> <!--(4)!-->
        <sessionId>s1</sessionId>
        <userId>ss</userId>
    </additionalData>
    <knowledge> <!--(5)!-->
        <facts>
            <description>Memories about current session</description>
            <fact>
                <name>UserName</name>
                <content>The user's name is Santanu.</content>
            </fact>
            <fact>
                <name>UserLocation</name>
                <content>The user is located in Bangalore.</content>
            </fact>
            <fact>
                <name>WeatherToday</name>
                <content>The weather in Bangalore today is sunny.</content>
            </fact>
        </facts>
    </knowledge>
</SystemPrompt>
```

1. System prompt provided to the `Agent` class constructor.
2. Tools registered with and discovered by the agent.
3. Secondary tasks provided by extensions
4. Request metadata passed to the agent
5. Facts provided by extensions and client

### User prompts

The mandatory `request` property passed in the `AgentInput<R>` parameter to the `execute*` methods is converted to a
structured XML object and wrapped in `<user_input>` tag.

For example, for the book summarizer agent, the provided `BookInfo` object is sent to the LLM as follows:
```xml
<user_input>
  <isbn>978-0393096729</isbn>
  <title>War and Peace</title>
</user_input>
```

Original input request for the above would be something like:
```java
agent.execute(
      AgentInput.<BookInfo>builder()
              .request(new BookInfo("978-0393096729", "War and Peace"))
              .build());
```

## Customizing Agent Behaviour

The `Agent` class constructor allows you to customize agent behaviour extensively by passing additional parameters. This
enables you to control how your agent handles tools, extensions, validation, and error handling.

### Constructor Parameters

| Parameter                | Type                                         | Mandatory | Description                                                                                       |
|--------------------------|----------------------------------------------|-----------|---------------------------------------------------------------------------------------------------|
| outputType               | Class&lt;T&gt;                               | Yes       | The class of the agent's output type.                                                             |
| systemPrompt             | String                                       | Yes       | The system prompt string for the agent.                                                           |
| setup                    | AgentSetup                                   | Yes       | The setup/configuration for the agent (model, mapper, retry, etc).                                |
| extensions               | List&lt;AgentExtension&lt;R, T, A&gt;&gt;    | No        | List of extensions to add custom logic, facts, or output schemas.                                 |
| knownTools               | Map&lt;String, ExecutableTool&gt;            | No        | Map of tool id to tool implementation for registering custom tools.                               |
| toolRunApprovalSeeker    | ToolRunApprovalSeeker&lt;R, T, A&gt;         | No        | (Advanced) Custom approval logic for tool runs.                                                   |
| outputValidator          | OutputValidator&lt;R, T&gt;                  | No        | (Advanced) Custom output validation logic.                                                        |
| errorHandler             | ErrorResponseHandler&lt;R&gt;                | No        | (Advanced) Custom error handling logic.                                                           |

For most use cases, you can use the simpler constructor with just `outputType`, `systemPrompt`, `setup`, `extensions`, and `knownTools`. For advanced customization, use the full constructor and pass your own implementations for `toolRunApprovalSeeker`, `outputValidator`, or `errorHandler`.

### Example: Basic Customization

```java
public class BookSummarizingAgent extends Agent<BookInfo, BookSummary, BookSummarizingAgent> {
    public BookSummarizingAgent(AgentSetup setup) {
        super(BookSummary.class,
              """
               You are an expert in summarizing books. You will be provided with the title and ISBN of a book.
               You need to summarize the book and provide the topics discussed in the book.
               """,
              setup,
              List.of(),
              Map.of());
    }
    
    @Override
    public String name() {
        return "book-summarizer";
    }
}
```

### Example: Advanced Customization

```java
public class CustomAgent extends Agent<MyRequest, MyResponse, CustomAgent> {
    public CustomAgent(AgentSetup setup,
                       ToolRunApprovalSeeker<MyRequest, MyResponse, CustomAgent> approvalSeeker,
                       OutputValidator<MyRequest, MyResponse> validator,
                       ErrorResponseHandler<MyRequest> errorHandler) {
        super(MyResponse.class,
              "Custom system prompt",
              setup,
              List.of(new MyExtension()),
              Map.of("myTool", new MyTool()),
              approvalSeeker,
              validator,
              errorHandler);
    }
    @Override
    public String name() {
        return "custom-agent";
    }
}
```

Use advanced options if you need to:

- approve or reject tool runs dynamically
- add custom validation logic for model outputs
- handle errors in a custom way

## Advanced Features

### Output Validation

The `OutputValidator` interface allows you to add custom validation logic for the model's generated response. If validation fails, Sentinel AI can automatically prompt the model to fix the errors.

```java
public class MyValidator implements OutputValidator<MyRequest, MyResponse> {
    @Override
    public OutputValidationResults validate(AgentRunContext<MyRequest> context, MyResponse output) {
        if (output.getSomeField() < 0) {
            return OutputValidationResults.builder()
                .failure(new ValidationFailure("someField must be non-negative"))
                .build();
        }
        return OutputValidationResults.success();
    }
}
```

Register it in the `Agent` constructor:

```java
super(MyResponse.class, systemPrompt, setup, extensions, tools, approvalSeeker, new MyValidator(), errorHandler);
```

### Message Pre-processors

`AgentMessagesPreProcessor` allows you to inspect or modify the list of messages just before they are sent to the LLM. This is useful for custom compaction, PII masking, or logging.

```java
agent.registerAgentMessagesPreProcessor((ctx, allMessages, newMessages) -> {
    // Modify messages if needed
    return AgentMessagesPreProcessResult.builder()
        .messages(allMessages) // Return the (modified) list
        .build();
});
```

## Extensions

Extensions are a way to add additional functionality to your agent. They are exposed as modules and can be used to
extend the functionality of the agent. Agents can be configured to use extensions by adding while creating the agent.
The extensions are loaded in the order they are added.

Extensions can be used to:

- Add facts to the knowledge passed to the agent in the system prompt
- Add custom tools to the agent
- Get agent to perform additional tasks
- Generate extra information from the agent

To create an extension derive and implement the `AgentExtension` interface.