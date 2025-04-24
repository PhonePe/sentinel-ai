# Introduction

Sentinel AI is a high level Java framework that allows you to build and deploy AI agents using a variety of LLMs and
tools. It is designed to be easy to use and flexible, allowing you to create agents that can perform a wide range of
tasks.

## Features

- **High level API**: Sentinel AI provides a high level API that makes it easy to create and deploy agents.
- **Flexible**: Sentinel AI is designed to be flexible, allowing you to create agents that can perform a wide range of
  tasks.
- **Easy to use**: Sentinel AI is easy to use, with a simple and intuitive API that makes it easy to get started.
- **Structured to the core**: Sentinel AI uses both structured prompts and output generation to allow for predictable
  behaviour from your AI agents.
- **Extensible**: Sentinel AI is designed to be extensible, allowing you to add your own custom tools and LLMs.
- **Modular**: The framework provides a variety of modules with extensions and tools that you can use to build your
  agents.

## Available Modules

Sentinel AI libraries are published on maven central. Sentinel-ai is arranged as modules:

- `sentinel-ai-core`: The core library that contains the main classes and interfaces for building agents.
- `sentinel-ai-model-simple-openai`: Using OpenAI api compliant models for agents.
- `sentinel-ai-embedding`: Provides embedding models to be used for indexing information in vector databases.
- `sentinel-ai-agent-memory`: Extension that implements memory extraction and storage from conversations.
- `sentinel-ai-session`: Extension that can be used to store and update information about a conversation session.
- `sentinel-ai-storage-es`: Elasticsearch based implementation for storage abstractions for agent memory, sessions etc.

## Getting Started

Building an agent with Sentinel involves broadly the following steps:

- Create agent class.
- Implement tools, toolboxes or add extensions
    - Implement agent memory to avoid sending all messages for context or be intelligent across sessions
    - Keep updating session with information to provide some context and a richer visual experience to the user
- Instantiate a model and configure it
- Instantiate an agent and run it

### Add the required dependencies

!!!note "Latest Version"
    The latest version of the library can be found [here](https://central.sonatype.com/namespace/com.phonepe.sentinel-ai){:target="_blank"}.

Sentinel AI should be included at the top level using the bom. To use the BOM, add the following to the
`dependencyManagement` section of your project.

```xml

<dependencyManagement>
    <dependencies>
        <!-- other stuff -->
        <dependency>
            <groupId>com.phonepe.sentinel-ai</groupId>
            <artifactId>sentinel-ai-bom</artifactId>
            <version>${sentinel-ai.version}</version>
            <scope>import</scope>
            <type>pom</type>
        </dependency>
    </dependencies>
</dependencyManagement>
```

The core abstractions for Sentinel AI are in the `sentinel-ai-core` module. To add it to your project add the following
dependencies:

```xml

<dependency>
    <groupId>com.phonepe.sentinel-ai</groupId>
    <artifactId>sentinel-ai-core</artifactId>
</dependency>
```

In order for the agent to work, it needs to use a model. Currently, Sentinel AI supports only OpenAI compliant models.
Use the following dependency to add the OpenAI model implementation to your project:

```xml

<dependency>
    <groupId>com.phonepe.sentinel-ai</groupId>
    <artifactId>sentinel-ai-models-simple-openai</artifactId>
</dependency>
```

!!!note "OpenAI Client Library"
    We use the [Simple OpenAI Client](https://github.com/sashirestela/simple-openai){:target="_blank"} instead of the official OpenAI client
    as this library provides much more flexibility to configure options on the HTTP client, something, typically needed in
    production environments to allow for tightened security and performance.

### Create Your Agent

The core AI agent abstraction in Sentinel AI is the generic `Agent` abstract class. The complexity of model interaction
is completely abstracted out from users. The `Agent` class requires the following type paramters:

- `R` - The type of the request object that the agent receives. Can be string or any other complex java types.
- `T` - The type of the response object that the agent returns. Can be string or any other complex java types.
- `A` - The agent subtype. This is the class you are currently implementing.

Basic things you'll need to supply to the agent to make it work are:

- The class type of the response type (This is a bit redundant but is needed for the serialization/deserialization
  framework inside the agent to work)
- The system prompt for the agent. This is the prompt that will be used to initialize the agent and provide context for
  the conversation.
- The agent setup. This is the configuration object that will be used to configure the agent. Models and other settings
  can be put here.
- Other optional parameters like tools, toolboxes, extensions etc.

Here is the implementation for a very simple text based agent:

```java
public class TestAgent extends Agent<String, String, TestAgent> {

    public TestAgent(@NonNull AgentSetup setup) {
        super(String.class, //(1)!
              "Greet the user", //(2)!
              setup, //(3)!
              List.of(),
              Map.of());
    }

    @Override
    public String name() { //(4)!
        return "test-agent";
    }
}
```

1. Return type of the agent
2. System prompt
3. Agent setup
4. A human-readable name for the agent. This is used for logging and debugging purposes.

### Create Your Agent Setup

This will broadly consist of the following steps:

- Create Jackson ObjectMapper for serialization/deserialization
- Create model object and configure it
- Create/configure relevant http client
- User agent setup to configure:
    - Model
    - ObjectMapper
    - Model Settings (like temperature, max tokens etc.)
- Pass the agent setup to the agent

Sample code for creating the agent setup:

```java
final var objectMapper = JsonUtils.createMapper(); //(1)!

final var httpClient = new OkHttpClient.Builder().build(); //(2)!

final var model = new SimpleOpenAIModel<>(
        "gpt-4o", //(3)!
        SimpleOpenAI.builder() //(4)!
                .baseUrl(EnvLoader.readEnv("OPENAI_ENDPOINT"))
                .apiKey(EnvLoader.readEnv("OPENAI_API_KEY"))
                .objectMapper(objectMapper)
                .clientAdapter(new OkHttpClientAdapter(httpClient))
                .build(),
        objectMapper //(5)!
);

final var agentSetup = AgentSetup.builder()
        .model(model) //(6)!
        .mapper(objectMapper) //(7)!
        .modelSettings(ModelSettings.builder() //(8)!
                               .temperature(0.1f)
                               .seed(1)
                               .build())
        .build();
```

1. Creates a preconfigured Object mapper with all the required modules and settings.
2. Creates a OkHttp based HTTP client with default settings.
3. The model name to use.
4. Simple-OpenAI client builder.
5. ObjectMapper to be used by the model.
6. The configured model.
7. The mapper used internally by the agent for setup.
8. Settings for the model. This will depend on the model.

### Bringing it all together

We create a small app that does interactive chat.

```java title="SimpleTextAgentExample.java"
public class SimpleTextAgentExample {

    public static class TestAgent extends Agent<String, String, TestAgent> {

        public TestAgent(AgentSetup setup) {
            super(String.class,
                  "Converse with the user on any topic",
                  setup,
                  List.of(),
                  Map.of());
        }

        @Override
        public String name() {
            return "test-agent";
        }
    }

    public static void main(String[] args) throws Exception {
        final var objectMapper = JsonUtils.createMapper();

        final var httpClient = new OkHttpClient.Builder().build();

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

        final var agentSetup = AgentSetup.builder()
                .model(model)
                .mapper(objectMapper)
                .modelSettings(ModelSettings.builder()
                                       .temperature(0.1f)
                                       .seed(1)
                                       .build())
                .build();

        final var agent = new TestAgent(agentSetup);
        final var requestMeta = AgentRequestMetadata.builder()
                .sessionId(UUID.randomUUID().toString())
                .build();
        var response = (AgentOutput<String>) null;
        Terminal terminal = TerminalBuilder.builder().system(true).build();
        LineReader lineReader = LineReaderBuilder.builder().terminal(terminal).build();

        String prompt = "> ";
        String userInput;
        while ((userInput = lineReader.readLine(prompt)) != null) {
            if (userInput.equalsIgnoreCase("exit")) {
                break;
            }
            response = agent.execute(AgentInput.<String>builder()
                                             .request(userInput)
                                             .requestMetadata(requestMeta)
                                             .oldMessages(null != response ? response.getAllMessages() : null)
                                             .build());
            System.out.println(response.getData());
        }
    }
}

```

You can now run the agent to converse with it. The agent will keep the context of the conversation and will be able to
respond to your queries.
