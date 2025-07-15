# Using MCP Servers

An Agent by itself does very little. Most of the power comes from it's ability to invoke tools or a set of tools to
perform a task. SentinelAI supports tool calling as a standard feature as seen in the section on [Tools](tools.md).

MCP servers are servers that implement the [Model Context Protocol](https://modelcontextprotocol.org/) (MCP) and
expose tools that can be used by agents. These servers can be run locally or remotely and can be used to register
tools with the agent.

SentinelAI supports using MCP servers to register tools. This is useful when you want to create a library of tools
and host them centrally to be used by multiple agents without needing to write them. There are many other open-source and
useful MCP servers available on the internet.

!!!note "Support for MCP functionality"
    MCP servers are servers that implement the [Model Context Protocol](https://modelcontextprotocol.org/) (MCP) and
    expose tools that can be used by agents. These servers can be run locally or remotely and can be used to register
    tools with the agent.

!!!note "MCP Transport"
    SentinelAI supports two types of MCP transports: `stdio` and `sse`. The `stdio` transport is used for local servers
    that can be run using a command line interface, while the `sse` transport is used for remote servers that support
    Server-Sent Events (SSE).

!!!danger "MCP Server security"
    Please ensure MCP servers you decide to use off the internet are secure and come from trusted sources. Also scope
    exposure of tools provided by MCP servers to the minimum required for your agent to function. Please go through the
    following sections to understand how this can be achieved in SentinelAI.

The toolbox can be added to your project using the following dependency:

```xml

<dependency>
    <groupId>com.phonepe.sentinel-ai</groupId>
    <artifactId>sentinel-ai-toolbox-mcp</artifactId>
    <version>${sentinel.version}</version>
</dependency>
```
This provides the `MCPToolBox` and `ComposingToolBox` classes t oconnect to and use tools from MCP servers.

## Using MCPToolBox

Then create the MCP client:

```java title="TestAgent.java"
// Build the client as prescribed in https://modelcontextprotocol.io/sdk/java/mcp-client
final var params = ServerParameters.builder("npx")
                .args("-y", "@modelcontextprotocol/server-everything")
                .build();
final var transport = new StdioClientTransport(params);

final var mcpClient = McpClient.sync(transport)
        .build();
mcpClient.initialize();
```

Create the `MCPToolBox` and register the toolbox to the agent:

```java title="TestAgent.java"
//Create and register a toolbox to the agent
final var mcpToolBox = new MCPToolBox("test", mcpClient, objectMapper, Set.of());
agent.registerToolbox(mcpToolBox);
```

This will register all the tools from the mcp server with the agent.

!!!note "Toolbox Name"
    All ToolBoxes should be given a meaningful name. This is used to generate unique ids for all the tools exposed by
    the MCPServer

### Filtering tools from MCP server

One of the major problems with using multiple or large MCP servers is that it will make available a lot of tools that
might be irrelevant to the context of the agent, but will still end up taking space in the context and tend to confuse
the LLM. To mitigate this, SentinelAI provides a way to filter the tools being exposed to the agent.

The `MCPToolBox` constructor accepts a set of tool names which are the identifiers for the tools.

```java
//This will expose only the "echo" and "sum" tools to the LLM
final var mcpToolBox = new MCPToolBox("test", mcpClient, objectMapper, Set.of("echo", "sum"));
```

This can be done programatically later as well using the `exposeTools` method as follows:

```java title="TestAgent.java"
mcpToolBox.exposeTools("echo", "sum");
```

You can call this method repeatedly to add more tools to the set of exposed tools. If you want to expose all tools you
can use the folliwing method:

```java title="TestAgent.java"
mcpToolBox.exposeAllTools();
```
This will reset the set of exposed tools to all the tools available in the MCP server.

## Connecting to multiple MCP servers

For more complex use, cases, you might want to connect to multiple MCP servers. However, the authors of most servers
would expose many tools for each server that may not be relevant to your user case. To solve this problem, SentinelAI
supports connecting to multiple MCP servers using the `ComposingToolBox`.

This toolbox allows you to:

- use `mcp.json` file to define multiple MCP servers to connect to
- Create a `ComposingToolBox` and register multiple MCP clients with ability to expose selected tools in each of them

### Using `mcp.json` to build

Over time, a standard way oif configuring MCP servers has emerged. The `mcp.json` file is a JSON file that contains a
map of MCP servers with relevant coordinates and parameters.

Format:
```json
{
  "mcpServers": { //(1)!
    "everythingServer": { //(2)!
      "type": "stdio", //(3)!
      "command": "npx", //(4)!
      "args": [ //(5)!
        "-y",
        "@modelcontextprotocol/server-everything"
      ],
      "env" : { //(6)!
        "VAR1": "value1",
        "Var2": "value2"
      },
      "exposedTools": ["sum", "echo"] //(7)!
    },
    "someOtherServer": {
      "type": "sse", //(8)!
      "url": "http://localhost:3001", //(9)!
      "timeout": 5000, //(10)!
      "knownTools": ["getUserLocation", "getUserName"]
    }
  }
}
```

1. The `mcpServers` key contains a map of MCP server names to their configurations.
2. A unique name for the MCP server. This is used to identify the server in the code.
3. The `type` of the MCP server. Currently, only `stdio` and `sse` are supported. For locally running servers this
   should be set to `stdio`.
4. Mandatory for `stdio` type servers, this is the command to run the MCP server. The `args` field contains the
   arguments to pass to the command.
5. The `args` field contains the arguments to pass to the command. This is an array of strings. This is optional.
6. The `env` field is optional and contains environment variables to set when starting the MCP server. This is optional.
7. The `exposedTools` field is a list of tool names that should be exposed to the agent. This is optional and
   sentinel defaults to exposing all tools exposed by the MCP server to the LLM. This is useful to filter out tools
   that are not relevant to the agent. **This is not a standard field and is specific to SentinelAI.**
8. For remote servers with Server-Sent Events (SSE) support, the `type` is `sse`.
9. The `url` field is the URL of the remote MCP server. This is mandatory for `sse` type servers.
10. Timeout in milliseconds for the connection to the remote MCP server. This is optional and defaults to 5000ms.

!!!tip "Ensuring efficiency"
    When using multiple MCP servers, it is recommended to expose only the tools that are relevant to the agent's
    functionality. Please refer to documentation of the relevant MCP servers to set proper parameters and expose only
    relevant tools.

To create and use `ComposingToolBox`, you can use the following code:

```java title="TestAgent.java"
// Create a ComposingToolBox using the mcp.json file
final var composingMCPToolBox = ComposingMCPToolBox.builder()
                .name("MCP Servers for Test Agent")
                .objectMapper(objectMapper)
                .mcpJsonPath("/path/to/mcp.json")
                .build();
agent.registerToolbox(mcpToolBox);
```

It is possible to load multiple mcp json files into a pre-built `ComposingToolBox`:

```java title="TestAgent.java"
MCPJsonReader.loadFile("/path/to/mcp.json", //Path to the mcp.json file
                       mcpToolBox, //Pre built ComposingMCPToolBox. does not matter how it was created
                       objectMapper); //For serialization and deserialization of tools, arguments etc
```

### Adding MCP servers programmatically
Clients to MCP servers can be added to `ComposingToolBox` programmatically as well.

To build a `ComposingToolBox` programmatically, you can use the following code:

```java title="TestAgent.java"
// Build the client as prescribed in https://modelcontextprotocol.io/sdk/java/mcp-client
final var params = ServerParameters.builder("npx")
                .args("-y", "@modelcontextprotocol/server-everything")
                .build();
final var transport = new StdioClientTransport(params);

final var mcpClient = McpClient.sync(transport)
        .build();
mcpClient.initialize();

// Build toolbox
final val toolBox = ComposingMCPToolBox.builder()
        .objectMapper(objectMapper)
        .build();
        
toolBox.registerExistingMCP("everythingServer", mcpClient, "add");
toolBox.registerExistingMCP("someOtherServer", otherServerMcpClient, Set.of("getUserLocation", "getUserName"));
```

To override the exposed tools from a server, use the `exposeTools` method as follows: 

```java title="TestAgent.java"
toolBox.exposeTools("everythingServer", "echo", "sum");
toolBox.exposeTools("otherServer", Set.of("echo", "sum"));
```

To expose all tools from a server, use the `exposeAllTools` method as follows:

```java title="TestAgent.java"
toolBox.exposeAllTools("everythingServer");
```

!!!warning
    Please note the following nuances about the above methods:

       - Both the `exposeTools` and `exposeAllTools` methods can be used only after the MCP server has been registered.
       - The `exposeTools` operations are additive. The elements/set passed to `exposeTools` method will be added to the
         tools configure to be exposed from the server. If you want to remove some exposed tools use the `exposeAllTools`
         method to reset the list and then rebuild it.
       - Always rethink and ensure you are not exposing too many tools to the agent. This can lead to confusion and
         inefficiency in the LLM's responses.
