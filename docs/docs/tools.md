# Tool Call Support

Major power of an AI agent comes from it's ability to invoke a tool or a set of tools to perform a task. Sentinel AI
supports tool calling as a standard feature.

- Tools can be defined as part of the agent class itself and are auto-discovered during instantiation
- Tool methods can have complex object types as input and output. they can also optionally have an instance of
  `AgentRunContext` as the first parameter.
- Tool methods need to be described using the `@Tool` annotation
- Tool methods from other c lasses can be registered at runtime or during agent creation.
- SentinelAI provides an abstraction called `ToolBox` to allow developers to build libraries of tools that do related
  work. ToolBoxes can be registered with the agent dynamically.

## The `AgentRunContext` class

The `AgentRunContext` class is used to pass information between the agent and the tool. It can be passed as the first
parameter to the tool method. Along with other stuff, the context can be used to access the current user request being
processed, the request metadata and so on.

| **Member**        | **Type**               | **Description**                                                          |
|-------------------|------------------------|--------------------------------------------------------------------------|
| `runId`           | `String`               | An ID for this particular run, used to track the run in logs and events. |
| `request`         | `R`                    | The user request being processed.                                        |
| `requestMetadata` | `AgentRequestMetadata` | Metadata for the request, such as session and user information.          |
| `agentSetup`      | `AgentSetup`           | Required setup for the agent, including model and tool configurations.   |
| `oldMessages`     | `List<AgentMessage>`   | List of old messages exchanged during the agent's interaction.           |
| `modelUsageStats` | `ModelUsageStats`      | Tracks usage statistics for the model during this run.                   |

## Tools as part of the agent class

SentinelAI will auto register all methods defined as part of the agent class. The methods need to be annotated with
`@Tool` for this functionality to work.

```java title="TestAgent.java"
class TestAgent extends Agent<String, String, TestAgent> {

    @JsonClassDescription("Information about the user")
    private record UserInfo(//(1)!
            @JsonProperty(required = true)
            @JsonPropertyDescription("Name of the user")
            String name) {
    }

    public TestAgent(@NonNull AgentSetup setup) {
        super(String.class,
              "Greet the user",
              setup,
              List.of(),
              Map.of());
    }

    @Tool("Call this tool to get appropriate greeting for the user")//(2)!
    public String greet(final UserInfo userInfo) {
        return "Hello " + userInfo.name();
    }

    @Override
    public String name() {
        return "test-agent";
    }
}
```

1. Complex object type for the input parameter. Document everything properly using appropriate annotations.
2. Tool Description. Make it verbose and clear. This will be used to generate the tool description for the LLM.

!!!tip
    The `@JsonProperty` and `JsonPropertyDescription` annotations are used to mark the parameter as required and to
    provide a description for the parameter. It is highly recommended to provide documentation for the tool itself and
    it's parameters to help the LLM understand the usage for the tools better.

## Externally defined tools
External tools can be defined and registered with the agent at runtime or at startup. This is useful when you want to
create a library of tools that can be used by multiple agents. The tools can be defined as part of a `ToolBox` or
registered individually.

### Registering methods from other classes as tools
You can register methods from other classes as tools when instantiating the agent. SentinelAI provides the `ToolReader` 
utility class to easily read and register methods from other classes. The methods need to be annotated with
`@Tool` for this functionality to work.

Let's say the tool is defined in an external class called `ExternalClass`:

```java title="ExternalClass.java"
public class ExternalClass {
    @Tool("Get appropriate greeting for the user")
    public String greet(@JsonProperty(required = true)
                        @JsonPropertyDescription("Name of the user")
                        String name) {
        return "Hello " + name;
    }
}

```

These tools can be used when creating the agent like so:
```java title="TestAgent.java"
public class TestAgent extends Agent<String, String, TestAgent> {

    public TestAgent(@NonNull AgentSetup setup) {
        super(String.class,
              "Greet the user",
              setup,
              List.of(),
              ToolReader.readTools(new ExternalClass()));//(1)!
    }

    @Override
    public String name() {
        return "test-agent";
    }
}
```

1. The `ToolReader.readTools` method will read all the methods annotated with `@Tool` and register them with the agent. 
    The methods can have complex object types as input and output. They can also optionally have an instance of
   `AgentRunContext` as the first parameter.

As seen in the example, tools can be read and registered directly during agent creation, or by calling the
`registerTools` method.

### Using ToolBox
`ToolBox` is a very simple interface to define a set of tools that are related to each other. The tools can be registered
with the agent all together by registering the toolbox using the `registerToolbox` methods.

```java title="TestToolBox.java"
public class TestToolBox implements ToolBox {
    @Tool("Get appropriate greeting for the user")
    public String greet(@JsonProperty(required = true)
                        @JsonPropertyDescription("Name of the user")
                        String name) {
        return "Hello " + name;
    }
}
```

Create the agent as before:

```java title="TestAgent.java"
public class TestAgent extends Agent<String, String, TestAgent> {

    public TestAgent(@NonNull AgentSetup setup) {
        super(String.class,
              "Greet the user",
              setup,
              List.of(),
              Map.of());
    }

    @Override
    public String name() {
        return "test-agent";
    }
}
```

Register the toolbox(es) at runtime:

```java
final var agent = new TestAgent(agentSetup)
                        .registerToolbox(new TestToolBox());
//Use agent
```

!!!tip
    We recommend combining related functionality into toolboxes and making libraries out of them to be used across agent.
