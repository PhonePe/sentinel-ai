---
title: Examples
description: End-to-end example — Text-to-SQL interactive CLI agent
---

# Examples

## Text-to-SQL Agent

The `sentinel-ai-examples` module ships a fully working **Text-to-SQL CLI agent** that lets
you query an e-commerce SQLite database in plain English.  All source files live under
[`sentinel-ai-examples/src/main/java/com/phonepe/sentinelai/examples/texttosql/`](https://github.com/PhonePe/sentinel-ai/tree/main/sentinel-ai-examples/src/main/java/com/phonepe/sentinelai/examples/texttosql).

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
│   ├─ startRestServer               (Dropwizard on random port)      │
│   ├─ buildTrustedHttpClient        (OkHttp + auth interceptor)      │
│   ├─ buildOpenAIModel              (SimpleOpenAIModel)              │
│   ├─ buildAgentSetup               (ModelSettings + output mode)    │
│   ├─ buildSkillsExtension          (AgentSkillsExtension / SKILL.md)│
│   ├─ buildAgent                    (TextToSqlAgent)                 │
│   ├─ registerLocalTools            (LocalSqlTools via JDBC)         │
│   ├─ registerHttpToolbox           (HttpToolBox → Dropwizard)       │
│   └─ runInteractiveLoop            (stdin prompt → agent → stdout)  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
           │                               │
           ▼                               ▼
  ┌─────────────────┐           ┌─────────────────────┐
  │  LocalSqlTools  │           │  SqliteRestServer   │
  │  (JDBC / Java)  │           │  (Dropwizard REST)  │
  └─────────────────┘           └─────────────────────┘
           │                               │
           └───────────────┬───────────────┘
                           ▼
                  ┌─────────────────┐
                  │  ecommerce.db   │
                  │  (SQLite file)  │
                  └─────────────────┘
```

The agent has **two tool layers** on top of the same SQLite database:

| Layer | Class / File | Description |
|---|---|---|
| Local | `LocalSqlTools.java` | In-process JDBC calls — schema introspection, timestamp conversion, ASCII table rendering |
| Remote-HTTP | `sqlite-api.yml` + `SqliteRestServer.java` | HTTP calls to an embedded Dropwizard server exposing a REST CRUD API |

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

    private static final String SYSTEM_PROMPT = """
            You are an expert SQL assistant for an Indian e-commerce platform.
            ...
            Mandatory workflow for every question:
            1. Use get_db_schema tool to understand the data model.
            2. Compose a valid SQLite SELECT statement.
            3. Execute using sqlite-api_execute_query.
            4. Convert *_at timestamp columns with convert_epoch_to_local_dt.
            5. Call the output generator tool with the final SqlQueryResult.
            """;

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

The system prompt encodes the mandatory tool-call workflow directly so the model always
follows the same steps regardless of which question is asked.

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

`LocalSqlTools` implements `ToolBox` and exposes five tools via `@Tool`-annotated methods.
Each method's `name` attribute becomes the tool name the model sees:

| `@Tool` name | Method | What it does |
|---|---|---|
| `get_db_schema` | `getDatabaseSchema()` | Full DDL + column comments for all 5 tables |
| `get_table_row_counts` | `getTableRowCounts()` | Row count per table |
| `get_current_dt` | `getCurrentDateTime(timezone)` | Current epoch + human datetime in timezone |
| `convert_epoch_to_local_dt` | `convertEpochToLocalDateTime(epoch, tz)` | Epoch seconds → `yyyy/MM/dd HH:mm:ss` |
| `format_results_as_table` | `formatResultsAsTable(result)` | Renders `SqlQueryResult` as an ASCII table |

```java
@Tool(name = "get_db_schema",
      value = "Get the complete e-commerce database schema with all table and column descriptions. "
            + "ALWAYS call this first before generating any SQL query.")
@SneakyThrows
public String getDatabaseSchema() {
    try (Connection conn = connect()) {
        // reads sqlite_master and PRAGMA table_info() via JDBC
        ...
    }
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
agent.registerTools(ToolUtils.readTools(new LocalSqlTools(dbPath.toString())));
```

---

### 4. Remote-HTTP Toolbox — `sqlite-api.yml`

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

### 5. The Embedded REST Server — `SqliteRestServer`

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

### 6. Database Initialisation — `DatabaseInitializer`

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

### 7. Configuration — `CliConfig`

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

### 8. CLI Orchestration — `TextToSqlCLI`

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

    final Path dbPath  = initializeDatabase(config);       // (1)
    final String url   = startRestServer(dbPath, mapper);  // (2)

    final OkHttpClientAdapter http  = buildTrustedHttpClient(config);   // (3)
    final var model                 = buildOpenAIModel(config, http, mapper); // (4)
    final AgentSetup agentSetup     = buildAgentSetup(config, model, mapper); // (5)

    final var skills = buildSkillsExtension();                // (6)
    final TextToSqlAgent agent = buildAgent(agentSetup, skills); // (7)

    registerLocalTools(agent, dbPath);       // (8)
    registerHttpToolbox(agent, url, mapper); // (9)

    printBanner();
    printExamples();

    return runInteractiveLoop(agent, config, effectiveSessionId); // (10)
}
```

| Step | Method | What it does |
|---|---|---|
| 1 | `initializeDatabase` | Creates + seeds the SQLite file if absent |
| 2 | `startRestServer` | Starts the Dropwizard server on a free port |
| 3 | `buildTrustedHttpClient` | Builds an `OkHttpClient` with a trust-all SSL context and an auth-injection interceptor |
| 4 | `buildOpenAIModel` | Creates a `SimpleOpenAIModel<SqlQueryResult>` wired to the configured endpoint |
| 5 | `buildAgentSetup` | Sets temperature, max tokens, and `TOOL_BASED` output mode |
| 6 | `buildSkillsExtension` | Loads `SKILL.md` from classpath (or `--skills-dir`) |
| 7 | `buildAgent` | Constructs `TextToSqlAgent` with the extension and a pass-through validator |
| 8 | `registerLocalTools` | Scans `LocalSqlTools` for `@Tool` methods and registers them |
| 9 | `registerHttpToolbox` | Loads `sqlite-api.yml` and registers the `HttpToolBox` |
| 10 | `runInteractiveLoop` | Enters the read-eval-print loop |

---

### 9. Interactive Loop

The REPL reads from `stdin` and dispatches each non-empty, non-exit line to
`handleQuery()`:

```java
private static int runInteractiveLoop(TextToSqlAgent agent, CliConfig config, String sessionId) {
    final var console = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    String line;
    while (true) {
        System.out.print("\n> ");
        line = console.readLine();
        if (line == null) break;                          // EOF (Ctrl+D)
        if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
            System.out.println("Goodbye!"); break;
        }
        handleQuery(agent, config, line.trim(), sessionId);
    }
    return 0;
}
```

`handleQuery` drives the agent in **streaming** or **non-streaming** mode
depending on `config.getAgent().isStreaming()`:

```java
// Streaming mode — tokens arrive in real time
final var future = agent.executeAsyncStreaming(
        AgentInput.<String>builder()
                .request(question)
                .requestMetadata(AgentRequestMetadata.builder()
                        .sessionId(sessionId)
                        .userId("cli-user")
                        .build())
                .build(),
        chunk -> System.out.print(new String(chunk, StandardCharsets.UTF_8)));
final var output = future.join();

// Non-streaming mode — wait for the full structured result
final var output = agent.executeAsync(AgentInput.<String>builder()...build()).join();
printStructuredResult(output.getData(), wallClockMs);
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

