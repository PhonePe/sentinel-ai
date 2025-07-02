# Accessing functionality from remote services

An Agent by itself does very little. Most of the power comes from it's ability to invoke tools or a set of tools to
perform a task. SentinelAI supports tool calling as a standard feature as seen in the section on [Tools](tools.md).

In modern architectures, overall functionality of an organization is often split into multiple services often deployed
on containerized environments. These services expose APIs that can be called by the agents to perform tasks. SentinelAI
provides multiple ways of accessing functionality from remote services. The following sections describe the different
ways in which an agent written using SentinelAI can access functionality provided by remote services.

!!!tip
    If you have not done already, please go through the concept of [ToolBoxes](tools.md#using-toolbox){:target="_blank"}
    and how to register them to an agent.

## Using MCP servers

SentinelAI supports using MCP servers to register tools. This is useful when you want to create a library of tools
and host them centrally to be used by multiple agents without needing to write them. Right now sentinel supports _only_
tool calls from mcp servers. (MCP servers also support resources, system prompt templates etc. You can find more details
at [https://modelcontextprotocol.io](https://modelcontextprotocol.io){:target="_blank"}).

Sentinel provides a special toolbox you can use to register the mcp server. The toolbox can be added to your project
using the following dependency:

```xml

<dependency>
    <groupId>com.phonepe.sentinel-ai</groupId>
    <artifactId>sentinel-ai-toolbox-mcp</artifactId>
    <version>${sentinel.version}</version>
</dependency>
```

Then create the MCP client:

```java title="TestAgent.java"
// Initialize the MCP client
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

## Calling remote service APIs using HTTP calls

While MCP servers provide a quick way to talk to services, one of the major problems with them is that they expose a
large number of tools to the agent, which can be overwhelming for the LLM. SentinelAI provides a way to call remote
service APIs using HTTP calls directly from the agent using the `HttpToolBox`.

### Nomenclature

We use the following nomenclature in rest of this section:

- **Upstream** - The remote service that the agent will call.
- **Endpoint** - The URL endpoint of the upstream service that the agent will call. This is a prefix to the api path in
  the tool.
- **Api** - A remote HTTP API defined by `{Method, Path, Headers, Body}`.
- **Template** - An object that contains the type and the template for a component of the specification.
  For example: { type: TEXT_SUBSTITUTION, content: "/apis/v1/location/${user}"}.

### Getting started with `HttpToolBox`

HttpToolBox is implemented in a separate module called `sentinel-ai-toolbox-remote-http`. You can add it to your project
using the following dependency:

```xml

<dependency>
    <groupId>com.phonepe.sentinel-ai</groupId>
    <artifactId>sentinel-ai-toolbox-remote-http</artifactId>
</dependency>
```

A single HTTP toolbox represents calls to a single upstream service. This approach has several advantages:

* The calls to a particular upstream can be grouped together making it similar to using an MCP server being exposed by
  the service
* Configurations such as timeouts, auth header injection, SSL certificate handling and other options to the HTTP client
  can be configured based on the service being called.
* Discovery of the actual service endpoint can be different for upstream to upstream if needed, especially for
  containerized/hybrid environments.

### HTTP Tool Definitions

Sentinel provides a convenient way to define remote HTTP calls as simple tools that are exposed to the LLM. The
complexity of the path, body, headers are completely abstracted out. The LLM sees only simple functions and native
parameters. To achieve this, Sentinel Abstracts the core `HttpTool` as `TemplatizedHttpTool`. A TemplatizedHttpTool has
the following requirements:

- **metadata** - Consisting of name, description and parameters for the tool
- **template** - consisting of the HTTP method, path, headers and body templates

#### HTTP Tool Metadata

| **Property** | **Type**                             | **Description**                                                                           |
|--------------|--------------------------------------|-------------------------------------------------------------------------------------------|
| name         | `String`                             | The name of the tool being registered.                                                    |
| description  | `String`                             | A detailed description of the tool, used by the LLM to select and use it.                 |
| parameters   | `Map<String, HttpToolParameterMeta>` | List of parameters that the LLM needs to pass to the tool. Map key is the parameter name. |

**HttpToolParameterMeta** - Metadata for tool parameters

| **Property** | **Type**                | **Description**                                                                        |
|--------------|-------------------------|----------------------------------------------------------------------------------------|
| description  | `String`                | A description for the parameter to help the LLM send correct values.                   |
| type         | `HttpToolParameterType` | Type of the parameter. Only simple types are supported for performance and simplicity. |

The following parameter types are supported:

- `STRING`
- `BOOLEAN`
- `INTEGER`
- `LONG`
- `FLOAT`
- `DOUBLE`
- `BYTE`
- `SHORT`
- `CHARACTER`
- `STRING_ARRAY`
- `BOOLEAN_ARRAY`
- `INTEGER_ARRAY`
- `LONG_ARRAY`
- `FLOAT_ARRAY`
- `DOUBLE_ARRAY`
- `BYTE_ARRAY`
- `SHORT_ARRAY`
- `CHARACTER_ARRAY`

#### Response Transformation
The HTTP toolbox supports transformation of the response of the API call. This is currently supported only for JSON 
responses. Json transformation is achieved using the powerful [JOLT](https://github.com/bazaarvoice/jolt){:target="_blank"} transformation library.

!!!tip "Testing out JOLT Transformations"
    You can test out JOLT transformations using the [JOLT Transform Tool](https://jolt-demo.appspot.com/){:target="_blank"}.

#### HTTP Tool Template

The template for the HTTP tool is a simple object that contains the following properties:

| **Field**   | **Type**                      | **Required** | **Description **                                                                                                      |
|-------------|-------------------------------|--------------|-----------------------------------------------------------------------------------------------------------------------|
| method      | `HttpCallSpec.HttpMethod`     | Yes          | The HTTP method to use for the call.                                                                                  |
| path        | `Template`                    | Yes          | The path to call, can be a template. Example: `/api/v1/location/${user}`                                              |
| headers     | `Map<String, List<Template>>` | No           | Headers to send with the call, each value can be a template. Example: `{"Authorization": "Bearer ${token}"}`          |
| body        | `Template`                    | No           | The body of the HTTP call, can be a template. Example: `{"name": "${name}"}`. Supported only in POST and PUT methods. |
| contentType | `String`                      | No           | The content type of the body, if applicable. Example: `application/json`                                              |

Sample code to create a `TemplatizedHttpTool`:

```java title="TestTool.java"
final var tool = TemplatizedHttpTool.builder()
        .metadata(HttpToolMetadata.builder() //Create tool metadata
                          .name("getUserLocation")
                          .description("Get the location of the user")
                          .parameters(Map.of( //Define parameters for the tool
                                              "user", HttpToolParameterMeta.builder()
                                                      .description("Name of the user")
                                                      .type(HttpToolParameterType.STRING)
                                                      .build()))
                          .build())
        .template(HttpCallTemplate.builder() // Build the HTTP call template
                          .method(HttpCallSpec.HttpMethod.GET)
                          .path(Template.textSubstitutor("/api/v1/location/${user}"))
                          .headers(Map.of(
                                  "Authorization", List.of(Template.textSubstitutor("Bearer ${token}")),
                                  "Client-ID", List.of(Template.text("Agent-Vinod"))))
                          .build())
        .responseTransformations(ResponseTransformerConfig.builder() //(Optional) Response transformation config
                                     .type(ResponseTransformerConfig.Type.JOLT)
                                     .config("""
                                             [
                                               {
                                                  "operation": "shift",
                                                  "spec": {
                                                     "location": "userLocation"
                                                  }
                                               }
                                             ]
                                             """)
        .build();
```

The above code uses the `Template.text()` and `Template.textSubstitutor()` methods for simplicity.

#### HTTP Tool Source

A `ToolSource` implementation is used to store the definition of the HttpTool instances for the upstreams. A single
`ToolSource` can handle all definitions for all upstreams. SentinelAI provides a default implementation called
`InMemoryHttpToolSource` that stores these definitions in Memory in a thread-safe manner. You can also implement your
own on a more permanent storage if you want.

To create an `InMemoryHttpToolSource`, you can use the following code:

```java
final var toolSource = InMemoryHttpToolSource.builder()
        .mapper(objectMapper) // Optional, but recommended to be sent
        .build();
```

Parameters:

| **Parameter** | **Type**                 | **Required** | **Description**                                                                                                                                                                     |
|---------------|--------------------------|--------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| mapper        | ObjectMapper             | No           | Jackson ObjectMapper for JSON serialization. If null, a default mapper is created. Recommended to send                                                                              |
| expander      | HttpCallTemplateExpander | No           | Expander used to convert templates to HTTP call specs. If null, a default expander is created. Recommended to not send unless a new template engine is being implemented by client. |

#### Bulk loading predefined tools

SentinelAI provides a utility class called `HttpToolSourceReader` to read the tool definitions from a file and load them
into a ToolSource.

To do this, use the following code:

```java
//To read from file
HttpToolReaders.loadToolsFromYAML(Paths.get("/PATH/TO/YAML/FILE"),toolsource);
//To read from bytes
HttpToolReaders.loadToolsFromYAMLContent(Files.readAllBytes(Paths.get("/PATH/TO/YAML/FILE")),toolsource);
```

#### Remote HTTP Tool Definition YAML file format

The remote HTTP tool definitions are stored in a YAML file format. The file contains a map of upstream names to a list
of tools. Each tool has metadata and a definition that includes the HTTP method, path, headers, and body templates.

```yaml
# File is a map of upstream -> tool mapping
test: #(1)!
  tools: #(2)!
    - metadata:
        name: getName #(3)!
        description: Get the name of the user #(4)!
      definition:
        method: GET
        path:
          type: TEXT
          content: /api/v1/name
    - metadata:
        name: getLocation
        description: Get location for specified user
        parameters: #(5)!
          userName: #(6)!
            description: Name of the user #(7)!
            type: STRING #(8)!
      definition: #(9)!
        method: POST #(10)!
        path: #(11)!
          type: TEXT #(12)!
          content: /api/v1/location #(13)!
        body: #(14)!
          type: TEXT_SUBSTITUTOR
          content: |
            {
              "name": "${name}"
            }
```

1. Upstream name. This is used to group the tools together. The upstream name can be anything, but it is recommended
   to use a meaningful name that represents the service being called.
2. List of tools.
3. Name of the tool.
4. Description of the tool. This is used by the LLM to understand the purpose of the tool.
5. Parameters for the tool. This is a map of parameter name to parameter metadata. The metadata contains the
   description and type of the parameter. This is optional and only needed if some parameters need to be passed to the
   tool by the LLM in the tool call.
6. Name of the parameter. This is used to identify the parameter in the tool call.
7. Description of the parameter. This is used by the LLM to understand the purpose of the parameter.
8. Type of the parameter as defined in [HttpToolParameterType](#http-tool-definitions).
9. Tool definition.
10. HTTP Method. Can be GET, POST, PUT, DELETE.
11. A template for the path. The template can be a simple text or a text substitutor. The text substitutor allows
    the LLM to pass parameters to the tool call.
12. Type of the template. Can be TEXT or TEXT_SUBSTITUTOR. TEXT_SUBSTITUTOR allows the LLM to pass parameters to
    the tool call.
13. The actual path to call. Can be string if type is `TEXT` or a template pattern for `TEXT_SUBSTITUTOR`. The
    template pattern can contain parameters that the LLM can fill in.
14. Body template. Relevant only for POST and PUT methods. The body can be a simple text or a text substitutor.
    The text substitutor allows the LLM to pass parameters to the tool call.

!!!note
    The file contains HTTP call definitions for multiple upstreams. Each upstream can have multiple tools defined.

The above file can be read and loaded into the `ToolSource` implementation you have using the `HttpToolSourceReader`
utility class.
