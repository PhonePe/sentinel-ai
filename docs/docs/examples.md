---
title: Examples
description: End-to-end example — Text-to-SQL interactive CLI agent
---

# Examples

## Text-to-SQL Agent

The `sentinel-ai-examples` module ships a fully working **Text-to-SQL CLI agent** that lets
you query an e-commerce SQLite database in plain English.  All source files live under
[`sentinel-ai-examples/src/main/java/com/phonepe/sentinelai/examples/texttosql/`](https://github.com/PhonePe/sentinel-ai/tree/main/sentinel-ai-examples/src/main/java/com/phonepe/sentinelai/examples/texttosql).

> NOTE: <br>
>   This agent is not production ready and is only used for demonstration purpose for understanding the capabilities
>   provided by sentinel-ai. As such, you may only use it as a reference to develop real-world agentic applications with sentinel-ai.

For example, when the user provides a prompt like below in the CLI, then they may see the results shown below
```
> List top 3 sellers by order volume

┌──────────────────────┬──────────────┐
│ seller_name          │ total_orders │
├──────────────────────┼──────────────┤
│ TechGadgets India    │ 312          │
│ FashionHub           │ 287          │
│ HomeEssentials       │ 241          │
└──────────────────────┴──────────────┘
```

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                          TextToSqlCLI                               │
│                                                                     │
│  call()                                                             │
│   ├─ loadConfig / validateConfig   (CliConfig from YAML)            │
│   ├─ initializeDatabase            (SQLite file + seed data)        │
│   ├─ buildTrustedHttpClient        (OkHttp + auth interceptor)      │
│   ├─ buildOpenAIModel              (SimpleOpenAIModel)              │
│   ├─ buildAgentSetup               (ModelSettings + output mode)    │
│   ├─ buildSkillsExtension          (AgentSkillsExtension / SKILL.md)│
│   ├─ buildAgent                    (TextToSqlAgent)                 │
│   ├─ registerLocalTools            (LocalSqlTools + SchemaVectorStore)│
│   ├─ registerToolbox               (HTTP or MCP, based on --toolbox-mode)│
│   └─ runInteractiveLoop            (stdin prompt → agent → stdout)  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
           │                    │                      │
           ▼                    ▼                      ▼
  ┌─────────────────┐  ┌─────────────────────┐  ┌──────────────────┐
  │  LocalSqlTools  │  │  SqliteRestServer   │  │  SqliteMcpServer │
  │  (JDBC / Java + │  │  (Dropwizard REST)  │  │  (stdio or SSE)  │
  │  Lucene search) │  │  [--toolbox-mode    │  │  [--toolbox-mode │
  └─────────────────┘  │   HTTP, default]    │  │   MCP]           │
           │           └─────────────────────┘  └──────────────────┘
           │                    │                      │
           └────────────────────┴──────────────────────┘
                                ▼
                       ┌─────────────────┐
                       │  ecommerce.db   │
                       │  (SQLite file)  │
                       └─────────────────┘
```

The agent has **three tool layers** on top of the same SQLite database:

| Layer | Class / File | Description |
|---|---|---|
| Local | `LocalSqlTools.java` | In-process tools — hybrid schema search (Lucene), timestamp conversion, ASCII table rendering |
| Remote-HTTP | `sqlite-api.yml` + `SqliteRestServer.java` | HTTP calls to an embedded Dropwizard server exposing a REST CRUD API (default toolbox mode) |
| MCP | `SqliteMcpServer.java` | MCP server launched as a subprocess, accessed via stdio or SSE transport (enabled with `--toolbox-mode MCP`) |

---

## Code Walkthrough

### 1. The Agent — `TextToSqlAgent`

**File:** [`agent/TextToSqlAgent.java`](https://github.com/PhonePe/sentinel-ai/blob/main/sentinel-ai-examples/src/main/java/com/phonepe/sentinelai/examples/texttosql/agent/TextToSqlAgent.java)

Every Sentinel AI agent extends `Agent<R, T, A>`:

- **`R`** — request type (plain `String` question from the user)
- **`T`** — response type (`SqlQueryResult` record)
- **`A`** — self-type for the fluent builder (`TextToSqlAgent`)

```java
public class TextToSqlAgent extends Agent<String, SqlQueryResult, TextToSqlAgent> {

    private static final String SYSTEM_PROMPT =
            """
            You are an expert SQL assistant for an e-commerce SQLite database.
            Translate natural-language questions into SQL queries, execute them, and return structured results.
            Follow the sql-execution skill protocol for every request.
            If unable to proceed without user input, ask the user for clarification before continuing.
            The user's timezone is '%s'. Use this timezone for all date formatting and timestamp conversions.
            """.formatted(TimeZone.getDefault().toZoneId().toString());

    @Builder
    public TextToSqlAgent(@NonNull AgentSetup setup,
                          @Singular List<AgentExtension<String, SqlQueryResult, TextToSqlAgent>> extensions,
                          @NonNull OutputValidator<String, SqlQueryResult> outputValidator) {
        super(SqlQueryResult.class, SYSTEM_PROMPT, setup, extensions,
              Map.of(), new ApproveAllToolRuns<>(), outputValidator,
              new DefaultErrorHandler<>(), new NeverTerminateEarlyStrategy());
    }

    @Override
    public String name() { return "text-to-sql-agent"; }
}
```

The system prompt is intentionally minimal — it delegates the full step-by-step execution
protocol (schema discovery, SQL generation, timestamp conversion, formatting) to the
`sql-execution` skill loaded via `AgentSkillsExtension`.  This keeps the agent class
decoupled from protocol details and makes the workflow easy to update without touching Java code.

---

### 2. The Output Type — `SqlQueryResult`

**File:** [`agent/SqlQueryResult.java`](https://github.com/PhonePe/sentinel-ai/blob/main/sentinel-ai-examples/src/main/java/com/phonepe/sentinelai/examples/texttosql/agent/SqlQueryResult.java)

The agent's structured output is a plain Java record annotated for JSON Schema generation:

```java
@JsonClassDescription("Result of executing a SQL query against a SQLite database.")
public record SqlQueryResult(
        @JsonPropertyDescription("The SQL statement generated from the natural-language request")
        String generatedSql,

        @JsonPropertyDescription("Rows returned. Each entry is a JSON string: "
                + "{\"col1\": val1, \"col2\": val2, ...}")
        List<String> results,

        @JsonPropertyDescription("Human-readable summary, caveats, or error description.")
        String explanation,

        @JsonPropertyDescription("Wall-clock time in milliseconds from submission to receipt.")
        long executionTimeMs
) {}
```

Sentinel AI uses the `@JsonClassDescription` / `@JsonPropertyDescription` annotations to
derive the JSON Schema that is sent to the model as the output tool definition —
no manual schema authoring required.

---

### 3. Local Tools — `LocalSqlTools`

**File:** [`tools/LocalSqlTools.java`](https://github.com/PhonePe/sentinel-ai/blob/main/sentinel-ai-examples/src/main/java/com/phonepe/sentinelai/examples/texttosql/tools/LocalSqlTools.java)

`LocalSqlTools` implements `ToolBox` and exposes seven tools via `@Tool`-annotated methods.
Each method's `name` attribute becomes the tool name the model sees:

| `@Tool` name | Method | What it does |
|---|---|---|
| `search_schema` | `searchSchema(query, topK)` | Hybrid BM25 + semantic search over table/column descriptions — **first call in every query** |
| `get_table_desc` | `getTableDescription(tableDescRequest)` | Full description (columns, types, nullability, semantics) for a list of tables |
| `get_column_desc` | `getColumnDescription(tableName, columnName)` | Description of a specific column |
| `get_table_row_counts` | `getTableRowCounts()` | Row count per table |
| `get_current_dt` | `getCurrentDateTime(timezone)` | Current epoch + human datetime in timezone |
| `convert_epoch_to_local_dt` | `convertEpochToLocalDateTime(epoch, tz)` | Epoch seconds → `yyyy/MM/dd HH:mm:ss` |
| `format_results_as_table` | `formatResultsAsTable(result)` | Renders `SqlQueryResult` as an ASCII table |

The constructor now accepts a `dataDir` argument used to persist the Lucene vector store index:

```java
@Tool(name = "search_schema",
      value = "Search the database schema using hybrid keyword and semantic search. "
            + "Returns the most relevant tables and columns for your question. "
            + "Use this to find which tables/columns to query before writing SQL.")
public String searchSchema(String query, int topK) {
    List<SchemaSearchResult> results = vectorStore.hybridSearch(query, topK);
    // formats results as a ranked list: index, type (TABLE/COLUMN), name, score, description
    ...
}

@Tool(name = "convert_epoch_to_local_dt",
      value = "Convert a Unix epoch timestamp (seconds) to a formatted date-time string "
            + "in the given IANA timezone.")
public String convertEpochToLocalDateTime(long epochSeconds, String timezone) {
    final ZonedDateTime zdt = Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.of(timezone));
    return zdt.format(DISPLAY_FORMAT); // yyyy/MM/dd HH:mm:ss
}
```

These tools are registered with the agent at runtime:

```java
// in TextToSqlCLI.registerLocalTools()
final Path dataDir = dbPath.getParent();
agent.registerTools(ToolUtils.readTools(new LocalSqlTools(dbPath.toString(), dataDir)));
```

---

### 4. Hybrid Schema Search — `SchemaVectorStore`

**Files:** [`tools/vectorstore/SchemaVectorStore.java`](https://github.com/PhonePe/sentinel-ai/blob/main/sentinel-ai-examples/src/main/java/com/phonepe/sentinelai/examples/texttosql/tools/vectorstore/SchemaVectorStore.java), `HashTextEmbedder.java`, `VectorStoreInitializer.java`

Instead of a monolithic `get_db_schema` call that dumps the entire schema on every query,
the agent uses a **hybrid search** approach to retrieve only the tables and columns relevant
to the current question.  This keeps context windows small and improves accuracy.

#### How it works

The vector store is backed by **Apache Lucene** and combines two complementary retrieval signals:

| Signal | Algorithm | Field |
|---|---|---|
| Keyword | BM25 full-text search | `content` (natural-language description of each table/column) |
| Semantic | KNN cosine-similarity | `vector` (128-dim feature-hash embedding) |

The embedding model (`HashTextEmbedder`) is a lightweight, deterministic feature hasher —
no external embedding API is needed.  It captures word-level and character 3-gram patterns and
produces L2-normalised vectors suitable for cosine similarity.

**Hybrid scoring algorithm:**

```
1. Embed the query text → float[128] vector
2. Run BM25 search against the `content` field  → bm25Candidates
3. Run KNN search against the `vector` field    → knnCandidates
4. Union both candidate sets
5. Normalise each score set independently to [0, 1]
6. combinedScore = 0.5 × normBM25 + 0.5 × normKNN
7. Return top-K results sorted by combinedScore DESC
```

The Lucene index is written to `{dataDir}/lucene-schema-index/` the **first time** the CLI
starts and re-opened read-only on subsequent runs.  Indexed documents come from
`resources/db/schema_descriptions.json` which contains human-authored descriptions for every
table and column.

#### `search_schema` output format

```
## Schema search results for: "order delivery timestamp"

1. [TABLE] orders (score: 0.921)
   Core transaction table recording every customer purchase...

2. [COLUMN] orders.delivered_at (score: 0.887)
   Unix epoch seconds when the order was delivered to the customer...

3. [COLUMN] orders.ordered_at (score: 0.754)
   Unix epoch seconds when the order was placed...
```

The `sql-execution` skill instructs the model to call `search_schema` first, extract the
unique table names from the results, then call `get_table_desc` with those names to retrieve
full column metadata before writing any SQL.

---

### 5. Remote-HTTP Toolbox — `sqlite-api.yml` (default)

**File:** [`resources/http-tools/sqlite-api.yml`](https://github.com/PhonePe/sentinel-ai/blob/main/sentinel-ai-examples/src/main/resources/http-tools/sqlite-api.yml)

The HTTP toolbox is declared in YAML and backed by the embedded Dropwizard server.
Each entry under `sqlite-api.tools` maps to an HTTP endpoint:

```yaml
sqlite-api:
  tools:
    - metadata:
        name: execute_query           # → model sees "sqlite-api_execute_query"
        description: >
          Execute a raw SQL query against the SQLite e-commerce database.
          Returns rows for SELECT or affectedRows for DML.
        parameters:
          sql:
            description: The complete SQL statement to execute
            type: STRING
      definition:
        method: POST
        path:
          type: TEXT
          content: /api/sqlite/query
        body:
          type: TEXT_SUBSTITUTOR      # Apache Commons ${variable} interpolation
          content: |
            {"sql": "${sql}"}
        contentType: application/json

    - metadata:
        name: get_table_schema        # → model sees "sqlite-api_get_table_schema"
        parameters:
          tableName: {type: STRING}
      definition:
        method: GET
        path:
          type: TEXT_SUBSTITUTOR
          content: /api/sqlite/schema/${tableName}
```

The `HttpToolBox` is created with the toolbox name `"sqlite-api"`, so every tool
name is prefixed — `execute_query` becomes `sqlite-api_execute_query` in the model's
tool list. The base URL is injected at runtime once the embedded server has started:

```java
// in TextToSqlCLI.registerHttpToolbox()
final var httpToolBox = new HttpToolBox(
        "sqlite-api",
        new OkHttpClient.Builder().build(),
        toolSource,
        mapper,
        baseUrl);                       // e.g. "http://localhost:54321"
agent.registerToolbox(httpToolBox);
```

---

### 6. The Embedded REST Server — `SqliteRestServer`

**File:** [`server/SqliteRestServer.java`](https://github.com/PhonePe/sentinel-ai/blob/main/sentinel-ai-examples/src/main/java/com/phonepe/sentinelai/examples/texttosql/server/SqliteRestServer.java)

The CLI starts a Dropwizard application in a background daemon thread on a
dynamically chosen free port. This exposes the same SQLite database over HTTP so
that the `HttpToolBox` tools can call it:

```
POST /api/sqlite/query          — execute arbitrary SQL
GET  /api/sqlite/tables         — list all tables
GET  /api/sqlite/schema/{table} — column definitions
GET  /api/sqlite/info           — database metadata
GET  /api/sqlite/records/{tbl}  — read rows (optional filters)
POST /api/sqlite/records/{tbl}  — insert a record
PUT  /api/sqlite/records/{tbl}  — update matching rows
```

```java
// in TextToSqlCLI.startRestServer()
final int port    = SqliteRestServer.findFreePort();
final String url  = SqliteRestServer.startEmbedded(dbPath.toString(), port, mapper);
// url → "http://localhost:<port>"
```

`startEmbedded` launches the Dropwizard `run()` loop in a `CompletableFuture` and
then polls the TCP port until it responds (up to 30 seconds), so by the time the
method returns the server is ready to accept requests.

---

### 7. MCP Server — `SqliteMcpServer`

**File:** [`mcp/SqliteMcpServer.java`](https://github.com/PhonePe/sentinel-ai/blob/main/sentinel-ai-examples/src/main/java/com/phonepe/sentinelai/examples/texttosql/mcp/SqliteMcpServer.java)

`SqliteMcpServer` is an alternative to the embedded Dropwizard server.  It implements the
**Model Context Protocol (MCP)** and exposes the same SQLite operations as MCP tools.
The CLI launches it as a subprocess when `--toolbox-mode MCP` is specified.

#### MCP tools exposed

| MCP tool | Description |
|---|---|
| `execute_query` | Execute a read-only SELECT statement with optional parameterised values |
| `list_tables` | List all user-defined tables in the database |
| `get_table_schema` | Column definitions (name, type, nullable, default) for a specific table |
| `get_database_info` | Database metadata (path, table count, approximate size) |

Write DML operations (INSERT, UPDATE, DELETE, DROP, ALTER, CREATE) are explicitly blocked.

#### Transport modes

| `--mcp-server-mode` | Transport | How it works |
|---|---|---|
| `STDIO` (default) | stdin/stdout | CLI spawns the subprocess and exchanges MCP messages via pipes. Logging is redirected to stderr so it does not interfere with the protocol. |
| `SSE` | HTTP Server-Sent Events | Subprocess binds an embedded Jetty server on `--mcp-port` (default 8766). The CLI polls the port until it responds, then registers an `MCPSSEServerConfig` pointing at `http://localhost:<port>`. |

```java
// Stdio MCP toolbox registration (in TextToSqlCLI)
final var mcpConfig = MCPStdioServerConfig.builder()
        .command(javaCmd)
        .args(List.of("-cp", classpath,
                      "...SqliteMcpServer",
                      "--db-path", dbPath.toString()))
        .build();
final var mcpToolBox = MCPToolBox.buildFromConfig()
        .name("sqlite-mcp")
        .mapper(mapper)
        .mcpServerConfig(mcpConfig)
        .build();
agent.registerToolbox(mcpToolBox);

// SSE MCP toolbox registration
final var mcpConfig = MCPSSEServerConfig.builder()
        .url("http://localhost:" + port)
        .build();
```

The MCP toolbox exposes tools with the prefix `sqlite-mcp_` (e.g. `sqlite-mcp_execute_query`),
and the `sql-execution` skill references `sqlite-api_execute_query` for the HTTP mode.  Both
paths are handled transparently by the skill — the agent calls whichever variant is registered.

---

### 8. Database Initialisation — `DatabaseInitializer`

**File:** [`tools/DatabaseInitializer.java`](https://github.com/PhonePe/sentinel-ai/blob/main/sentinel-ai-examples/src/main/java/com/phonepe/sentinelai/examples/texttosql/tools/DatabaseInitializer.java)

On first launch the database file does not exist. `DatabaseInitializer.ensureInitialised()`
creates it, applies the bundled DDL, and seeds all five tables from CSV files:

```
resources/db/schema.sql       — CREATE TABLE statements with inline column comments
resources/db/data/users.csv
resources/db/data/sellers.csv
resources/db/data/catalog.csv
resources/db/data/inventory.csv
resources/db/data/orders.csv
```

Subsequent runs detect that the file already contains tables and skip the step.

---

### 9. Configuration — `CliConfig`

**File:** [`cli/CliConfig.java`](https://github.com/PhonePe/sentinel-ai/blob/main/sentinel-ai-examples/src/main/java/com/phonepe/sentinelai/examples/texttosql/cli/CliConfig.java)

All settings live in a YAML file (default: `.env/agent-config.yml`):

```yaml
openai:
  baseUrl: https://api.openai.com     # or your proxy / Azure endpoint
  apiKey:  sk-...
  model:   gpt-4o

database:
  path: ./ecommerce.db                # created automatically on first run

agent:
  temperature: 0.0                    # deterministic SQL generation
  maxTokens:   4096
  streaming:   true                   # stream tokens to stdout as they arrive
```

An example file is bundled at
`sentinel-ai-examples/src/main/resources/.env/agent-config.yml.example`.

---

### 10. CLI Orchestration — `TextToSqlCLI`

**File:** [`cli/TextToSqlCLI.java`](https://github.com/PhonePe/sentinel-ai/blob/main/sentinel-ai-examples/src/main/java/com/phonepe/sentinelai/examples/texttosql/cli/TextToSqlCLI.java)

`TextToSqlCLI` implements `Callable<Integer>` (picocli) and is the entry point.
The `call()` method is a pure orchestration sequence — each step is delegated to
its own private method:

```java
@Override
public Integer call() {
    final CliConfig config         = loadConfig(configPath);
    validateConfig(config);

    final String effectiveSessionId = resolveSessionId(sessionId);
    final ObjectMapper mapper       = JsonUtils.createMapper();

    final Path dbPath = initializeDatabase(config);              // (1)

    final OkHttpClientAdapter http  = buildTrustedHttpClient(config);        // (2)
    final var model                 = buildOpenAIModel(config, http, mapper); // (3)
    final AgentSetup agentSetup     = buildAgentSetup(config, model, mapper); // (4)

    final var skills = buildSkillsExtension();                               // (5)
    final TextToSqlAgent agent = buildAgent(agentSetup, skills);             // (6)

    registerLocalTools(agent, dbPath);                                       // (7)

    if (toolboxMode == ToolboxMode.MCP) {
        if (mcpServerMode == McpServerMode.SSE) {
            registerMcpToolboxSse(agent, dbPath, mapper, mcpPort);           // (8a)
        } else {
            registerMcpToolbox(agent, dbPath, mapper);                       // (8b)
        }
    } else {
        final String baseUrl = startRestServer(dbPath, mapper);
        registerHttpToolbox(agent, baseUrl, mapper);                         // (8c)
    }

    ConsoleUtils.printBanner();
    ConsoleUtils.printExamples();

    return runInteractiveLoop(agent, config, effectiveSessionId, mapper);    // (9)
}
```

| Step | Method | What it does |
|---|---|---|
| 1 | `initializeDatabase` | Creates + seeds the SQLite file if absent |
| 2 | `buildTrustedHttpClient` | Builds an `OkHttpClient` with a trust-all SSL context and an auth-injection interceptor |
| 3 | `buildOpenAIModel` | Creates a `SimpleOpenAIModel<SqlQueryResult>` wired to the configured endpoint |
| 4 | `buildAgentSetup` | Sets temperature, max tokens, and `TOOL_BASED` output mode |
| 5 | `buildSkillsExtension` | Extracts bundled `SKILL.md` to a temp dir (or uses `--skills-dir`) and builds `AgentSkillsExtension` |
| 6 | `buildAgent` | Constructs `TextToSqlAgent` with the skills extension and a pass-through output validator |
| 7 | `registerLocalTools` | Initialises `LocalSqlTools` (including the Lucene vector store) and registers all `@Tool` methods |
| 8a | `registerMcpToolboxSse` | Launches `SqliteMcpServer` subprocess in SSE mode; waits for port to open; registers `MCPToolBox` |
| 8b | `registerMcpToolbox` | Launches `SqliteMcpServer` subprocess in stdio mode; registers `MCPToolBox` |
| 8c | `startRestServer` + `registerHttpToolbox` | Starts embedded Dropwizard server and registers `HttpToolBox` (default) |
| 9 | `runInteractiveLoop` | Enters the read-eval-print loop |

---

### 11. Interactive Loop

The REPL reads from `stdin` and dispatches each non-empty line to `handleQuery()`.
Special commands are handled before the query is sent to the agent:

| Input | Behaviour |
|---|---|
| `exit` / `quit` | Graceful shutdown |
| EOF (`Ctrl+D`) | Graceful shutdown |
| `/dumpMessages [filename]` | Serialise all agent messages from the last query to a JSON file under `.logs/` |
| *(any other text)* | Forwarded to the agent as a natural-language question |

`handleQuery` drives the agent in **streaming** or **non-streaming** mode
depending on `config.getAgent().isStreaming()`:

```java
// Streaming mode — tokens arrive in real time
final var future = agent.executeAsyncStreaming(
        AgentInput.<String>builder()
                .request(question)
                .requestMetadata(AgentRequestMetadata.builder()
                        .sessionId(effectiveSessionId)
                        .userId("cli-user")
                        .build())
                .build(),
        chunk -> System.out.print(new String(chunk, StandardCharsets.UTF_8)));
final AgentOutput<SqlQueryResult> output = ConsoleUtils.awaitWithSpinner(future, true);

// Non-streaming mode — wait for the full structured result
final AgentOutput<SqlQueryResult> output =
        ConsoleUtils.awaitWithSpinner(agent.executeAsync(agentInput), true);
ConsoleUtils.printStructuredResult(output.getData(), wallClockMs);
```

The session ID threads through every call so the model retains conversation
history within a session. A new random UUID is generated if `--session-id` is
not provided on the command line.

---

## Running the Example

### Prerequisites

- Java 17+
- Maven 3.8+
- An OpenAI-compatible API key

### Build

```bash
cd sentinel-ai-examples
mvn package -DskipTests
```

### Configure

```bash
mkdir -p .env
cp src/main/resources/.env/agent-config.yml.example .env/agent-config.yml
# Edit .env/agent-config.yml and set openai.apiKey
```

### Run

```bash
java -jar target/sentinel-ai-examples-*-cli.jar
# or use the helper script
./run.sh
```

Available CLI options:

| Option | Default | Description |
|---|---|---|
| `--config`, `-c` | `.env/agent-config.yml` | Path to the YAML config file |
| `--skills-dir`, `-s` | *(bundled)* | Override the skills directory |
| `--session-id` | *(random UUID)* | Session ID for conversation history |
| `--toolbox-mode`, `-t` | `HTTP` | Toolbox for SQL execution: `HTTP` (embedded REST server) or `MCP` (MCP subprocess) |
| `--mcp-server-mode` | `STDIO` | MCP transport when `--toolbox-mode MCP`: `STDIO` or `SSE` |
| `--mcp-port` | `8766` | HTTP port for the MCP SSE subprocess (only used with `--mcp-server-mode SSE`) |

### Sample Queries

Once the banner appears, try any of the following:

```
> List top 3 sellers by order volume
> Find the user with the most number of orders
> Find out top cities by shoe sales
> What are the top 5 best-selling products this month?
> Show total revenue per product category
> Which products are running low on inventory?
> How many orders were placed in the last 30 days?
```

Type `exit` or `quit` (or press `Ctrl+D`) to stop.

