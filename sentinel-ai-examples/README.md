# Sentinel AI Examples — Text-to-SQL Agent

This module contains a fully working, end-to-end example of a **Text-to-SQL agent** built on top of
Sentinel AI. The agent accepts natural-language questions about an e-commerce SQLite database and
returns structured query results — including the generated SQL, query result set formatted as a table, 
a plain-English explanation, and execution time.

This document is a step-by-step guide to building the agent from scratch. You will understand every
design decision along the way.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Prerequisites](#2-prerequisites)
3. [Step 1 — Define the Output Type](#3-step-1--define-the-output-type)
4. [Step 2 — Design the Database Schema](#4-step-2--design-the-database-schema)
5. [Step 3 — Seed the Database](#5-step-3--seed-the-database)
6. [Step 4 — Build the Schema Vector Store](#6-step-4--build-the-schema-vector-store)
7. [Step 5 — Create the Local ToolBox](#7-step-5--create-the-local-toolbox)
8. [Step 6 — Build the Human-in-the-Loop Tool](#8-step-6--build-the-human-in-the-loop-tool)
9. [Step 7 — Write the Execution Skill](#9-step-7--write-the-execution-skill)
10. [Step 8 — Expose the Database via REST (HTTP Toolbox)](#10-step-8--expose-the-database-via-rest-http-toolbox)
11. [Step 9 — Alternatively, Expose the Database via MCP](#11-step-9--alternatively-expose-the-database-via-mcp)
12. [Step 10 — Implement the Agent](#12-step-10--implement-the-agent)
13. [Step 11 — Wire Everything Together in the CLI](#13-step-11--wire-everything-together-in-the-cli)
14. [Step 12 — Run the Agent](#14-step-12--run-the-agent)
15. [Project Layout Reference](#15-project-layout-reference)

---

## 1. Architecture Overview

The completed agent is structured in three layers of tools, all orchestrated by a single
`TextToSqlAgent`:

```
User NL Question
       │
       ▼
 TextToSqlCLI          ← PicoCLI entry point; interactive REPL
       │
       ▼
 TextToSqlAgent        ← extends Agent<String, SqlQueryResult, TextToSqlAgent>
       │
       ├── AgentSkillsExtension   ← injects SKILL.md as a secondary task protocol
       │
       ├── LocalTools (ToolBox)   ← in-process Java tools
       │     • search_schema          hybrid BM25 + KNN vector search
       │     • get_table_desc         semantic table descriptions
       │     • get_column_desc        single-column descriptions
       │     • get_table_row_counts   direct JDBC row counts
       │     • convert_epoch_to_local_dt
       │     • get_current_dt
       │     • format_results_as_table
       │
       ├── AskUserTool (ToolBox)  ← pauses the agent to ask the user
       │     • ask_user_question
       │     • ask_user_to_choose
       │
       └── (choose one at startup)
             HTTP mode → HttpToolBox → Dropwizard REST server (SqliteRestResource)
               • execute_query, list_tables, get_table_schema, get_database_info, …
             MCP mode  → MCPToolBox  → SqliteMcpServer subprocess
               • execute_query, list_tables, get_table_schema, get_database_info
```

**Key design principles:**

- **Local tools handle schema intelligence** — vector search, timestamp conversion, result
  formatting. These run in-process and need no network round-trip.
- **Remote tools handle query execution** — either via a REST server or an MCP subprocess. This
  separation means you can swap the execution backend without changing the agent.
- **A skill file encodes the 7-step protocol** — the agent reads the skill at startup and follows
  it on every request: search schema → resolve dates → generate SQL → confirm → execute →
  convert timestamps → return result.
- **Human-in-the-loop via tool call** — the agent pauses and surfaces a question to the user before
  executing any SQL. The user's answer is fed back into the model's context.

---

## 2. Prerequisites

| Requirement | Version |
|---|---|
| Java | 17+ |
| Maven | 3.9+ |
| An OpenAI-compatible API key | `gpt-4o` recommended |

Add the BOM and relevant Sentinel AI modules to your `pom.xml`.
See [pom.xml](pom.xml) for reference.

---

## 3. Step 1 — Define the Output Type

Every Sentinel AI agent produces a single structured output POJO. For the Text-to-SQL agent, define
a Java record called `SqlQueryResult` that captures everything a downstream consumer needs:

```
src/main/java/.../tools/model/SqlQueryResult.java
```

```java
@JsonClassDescription(
    "Result of executing a SQL query against a SQLite database. Contains the generated SQL, "
        + "the query results (if any), an explanation of the results or any errors, and the execution time.")
public record SqlQueryResult(
    @JsonPropertyDescription(
            "The SQL statement that was generated from the user provided natural-language request")
        String generatedSql,

    @JsonPropertyDescription(
            "Rows returned by the query. Each entry is a JSON string representing one row, "
                + "with the format {\"col1Name\": col1Value, \"col2Name\": col2Value, ...}")
        List<String> results,

    @JsonPropertyDescription(
            "A human-readable summary of what was done, what the results mean,"
                + " and any caveats or errors encountered.")
        String explanation,

    @JsonPropertyDescription(
            "Wall-clock time in milliseconds from query submission to result receipt.")
        long executionTimeMs) {}
```

**Why a record?** Sentinel AI uses Jackson to deserialise structured json output from the LLM. Records
work well here: they are immutable, concise, and Jackson can serialise/deserialise them without
annotations beyond `@JsonPropertyDescription` (which doubles as the field-level documentation that
the model reads in its output schema).

The `@JsonClassDescription` and `@JsonPropertyDescription` annotations are sent to the model as part
of the output schema — they guide the model to fill each field correctly.

---

## 4. Step 2 — Design the Database Schema

Place the DDL in `src/main/resources/db/schema.sql`. The e-commerce schema has five tables with a
deliberate design pattern: all timestamps are stored as **Unix epoch seconds** (INTEGER), not
ISO-8601 strings. This is important — the agent must learn to convert them.

```sql
-- Users who place orders
CREATE TABLE IF NOT EXISTS users (
    user_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    email         TEXT    NOT NULL UNIQUE,
    full_name     TEXT    NOT NULL,
    timezone      TEXT    NOT NULL DEFAULT 'UTC',
    city          TEXT,
    created_at    INTEGER NOT NULL,   -- epoch seconds
    last_login_at INTEGER             -- epoch seconds, nullable
);

-- Sellers who list products
CREATE TABLE IF NOT EXISTS sellers (
    seller_id     INTEGER PRIMARY KEY AUTOINCREMENT,
    seller_name   TEXT    NOT NULL,
    contact_email TEXT    NOT NULL UNIQUE,
    rating        REAL    NOT NULL DEFAULT 0.0,
    joined_at     INTEGER NOT NULL
);

-- Product catalog
CREATE TABLE IF NOT EXISTS catalog (
    product_id    INTEGER PRIMARY KEY AUTOINCREMENT,
    seller_id     INTEGER NOT NULL REFERENCES sellers(seller_id),
    product_name  TEXT    NOT NULL,
    category      TEXT    NOT NULL,
    subcategory   TEXT,
    price         REAL    NOT NULL,
    created_at    INTEGER NOT NULL,
    updated_at    INTEGER NOT NULL
);

-- Warehouse stock levels
CREATE TABLE IF NOT EXISTS inventory (
    inventory_id  INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id    INTEGER NOT NULL REFERENCES catalog(product_id),
    warehouse     TEXT    NOT NULL,
    quantity      INTEGER NOT NULL DEFAULT 0,
    reorder_level INTEGER NOT NULL DEFAULT 10,
    updated_at    INTEGER NOT NULL,
    UNIQUE (product_id, warehouse)
);

-- Purchase orders (one row = one line item)
CREATE TABLE IF NOT EXISTS orders (
    order_id      INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id       INTEGER NOT NULL REFERENCES users(user_id),
    product_id    INTEGER NOT NULL REFERENCES catalog(product_id),
    seller_id     INTEGER NOT NULL REFERENCES sellers(seller_id),
    quantity      INTEGER NOT NULL,
    unit_price    REAL    NOT NULL,
    total_amount  REAL    NOT NULL,   -- pre-computed: quantity × unit_price
    status        TEXT    NOT NULL DEFAULT 'pending',
    ordered_at    INTEGER NOT NULL,
    confirmed_at  INTEGER,
    shipped_at    INTEGER,
    delivered_at  INTEGER,
    cancelled_at  INTEGER
);
```

Add indexes for the query patterns you anticipate:

```sql
CREATE INDEX IF NOT EXISTS idx_catalog_seller    ON catalog   (seller_id);
CREATE INDEX IF NOT EXISTS idx_catalog_category  ON catalog   (category);
CREATE INDEX IF NOT EXISTS idx_inventory_product ON inventory (product_id);
CREATE INDEX IF NOT EXISTS idx_orders_user       ON orders    (user_id);
CREATE INDEX IF NOT EXISTS idx_orders_status     ON orders    (status);
CREATE INDEX IF NOT EXISTS idx_orders_ordered_at ON orders    (ordered_at);
```

---

## 5. Step 3 — Seed the Database

Create a `DatabaseInitializer` utility class. On first launch it:

1. Creates the SQLite file.
2. Runs the DDL from `schema.sql`.
3. Loads seed data from CSV files bundled in `src/main/resources/db/ecommerce-data/`.

The initializer is **idempotent** — it checks for existing tables before doing any work.

```
src/main/java/.../tools/DatabaseInitializer.java
```

```java
@Slf4j
@UtilityClass
public class DatabaseInitializer {

    private static final String[] TABLE_ORDER = {
        "users", "sellers", "catalog", "inventory", "orders"
    };

    public static void ensureInitialised(Path dbPath) throws Exception {
        if (Files.exists(dbPath) && isDatabasePopulated(dbPath)) {
            log.info("Database already exists — skipping initialisation");
            return;
        }
        Files.createDirectories(dbPath.getParent());
        try (Connection conn = connect(dbPath)) {
            conn.setAutoCommit(false);
            createSchema(conn);                             // runs schema.sql
            for (String table : TABLE_ORDER) {
                loadCsvData(conn, table);                   // loads <table>.csv
            }
            conn.commit();
        }
    }
    // ... private helpers: createSchema, loadCsvData, parseCsvLine, isDatabasePopulated
}
```

**CSV loading order matters** — `users` and `sellers` must be loaded before `catalog`, which must be
loaded before `inventory` and `orders`, because of the foreign key constraints.

Place your seed CSV files at:

```
src/main/resources/db/ecommerce-data/
├── users.csv
├── sellers.csv
├── catalog.csv
├── inventory.csv
└── orders.csv
```

---

## 6. Step 4 — Build the Schema Vector Store

The agent needs to find relevant tables and columns without being given the full schema upfront. We
build a **Lucene-backed hybrid search index** over semantic descriptions of every table and column.

### 6.1 Schema descriptions file

Create `src/main/resources/db/schema_descriptions.json`. Each entry describes a table and its
columns in plain English. These descriptions become the documents indexed in Lucene.

```json
{
  "tables": [
    {
      "name": "orders",
      "description": "Purchase transactions placed by users. Each row is one line item...",
      "primaryKeyColumns": ["order_id"],
      "columns": [
        {
          "name": "status",
          "dataType": "TEXT",
          "nullable": false,
          "description": "Order lifecycle state: pending / confirmed / shipped / delivered / cancelled"
        },
        {
          "name": "ordered_at",
          "dataType": "INTEGER",
          "nullable": false,
          "description": "Unix epoch seconds: when the order was placed"
        }
      ]
    }
  ]
}
```

### 6.2 Feature-hashing embedder

Rather than calling an external embedding model, use a lightweight feature-hashing embedder. It
produces 128-dimension L2-normalised vectors combining word-level hashing and character 3-gram
features — no network, no GPU, fully deterministic:

```
src/main/java/.../tools/vectorstore/HashTextEmbedder.java
```

```java
public class HashTextEmbedder {

    public static final int VECTOR_DIM = 128;

    public float[] embed(String text) {
        float[] vector = new float[VECTOR_DIM];
        String normalized = text.toLowerCase().replaceAll("[^a-z0-9]", " ");
        for (String token : normalized.trim().split("\\s+")) {
            // word-level feature
            vector[Math.abs(token.hashCode()) % VECTOR_DIM] += 1.0f;
            // character 3-gram features
            for (int i = 0; i <= token.length() - 3; i++) {
                vector[Math.abs(token.substring(i, i + 3).hashCode()) % VECTOR_DIM] += 0.5f;
            }
        }
        l2Normalize(vector);
        return vector;
    }
}
```

### 6.3 SchemaVectorStore

Build a Lucene index that stores both a `TextField` (for BM25 keyword search) and a
`KnnFloatVectorField` (for ANN cosine-similarity search) per document:

```
src/main/java/.../tools/vectorstore/SchemaVectorStore.java
```

```java
public class SchemaVectorStore implements AutoCloseable {

    private static final float BM25_WEIGHT = 0.5f;

    public void buildIndex(List<Map<String, String>> documents) throws IOException {
        try (IndexWriter writer = new IndexWriter(directory, config)) {
            for (Map<String, String> entry : documents) {
                Document doc = new Document();
                float[] vector = embedder.embed(entry.get("content"));
                doc.add(new StringField("doc_type",   entry.get("docType"),   Field.Store.YES));
                doc.add(new StringField("table_name", entry.get("tableName"), Field.Store.YES));
                doc.add(new TextField("content",      entry.get("content"),   Field.Store.YES));
                doc.add(new KnnFloatVectorField("vector", vector, VectorSimilarityFunction.COSINE));
                writer.addDocument(doc);
            }
        }
    }

    public List<SchemaSearchResult> hybridSearch(String query, int topK) throws IOException {
        // 1. BM25 keyword search
        // 2. KNN cosine-similarity search
        // 3. Normalise each score set to [0,1]
        // 4. Blend: combined = 0.5 * normBM25 + 0.5 * normKNN
        // 5. Sort descending, return top-K
    }
}
```

The hybrid approach is important: BM25 handles exact column-name matches ("ordered_at") while KNN
handles paraphrased queries ("when was the purchase made").

---

## 7. Step 5 — Create the Local ToolBox

`LocalTools` is a `ToolBox` whose `@Tool`-annotated methods become tools available to the LLM. It
wraps the vector store, the schema descriptions JSON, and direct JDBC access.

```
src/main/java/.../tools/LocalTools.java
```

```java
@Slf4j
public class LocalTools implements ToolBox {

    public LocalTools(String dbPath, Path dataDir) throws Exception {
        this.dbPath = dbPath;
        this.vectorStore = VectorStoreInitializer.ensureInitialized(dataDir);
        this.schemaDescriptions = loadSchemaDescriptions();
    }

    @Override
    public String name() { return "local_sql_tools"; }

    @Tool(name = "search_schema",
          value = "Search the database schema using hybrid keyword and semantic search. ...")
    public String searchSchema(String query, int topK) { ... }

    @Tool(name = "get_table_desc",
          value = "Get the full description of one or more tables from schema_descriptions.json. ...")
    public String getTableDescription(TableDescRequest request) { ... }

    @Tool(name = "get_column_desc",
          value = "Get the description of a specific column from schema_descriptions.json. ...")
    public String getColumnDescription(String tableName, String columnName) { ... }

    @Tool(name = "get_table_row_counts",
          value = "Get the row count for every table in the e-commerce database. ...")
    public String getTableRowCounts() { ... }

    @Tool(name = "convert_epoch_to_local_dt",
          value = "Convert a Unix epoch timestamp (seconds) to a formatted date-time string ...")
    public String convertEpochToLocalDateTime(long epochSeconds, String timezone) { ... }

    @Tool(name = "get_current_dt",
          value = "Get the current date and time in the specified IANA timezone. ...")
    public String getCurrentDateTime(String timezone) { ... }

    @Tool(name = "format_results_as_table",
          value = "Display query result rows from a SqlQueryResult into a clean ASCII table. ...")
    public static String formatResultsAsTable(SqlQueryResult result) { ... }
}
```

**Key patterns:**

- The `@Tool` annotation's `value` field is the tool description sent verbatim to the model. Write
  it to be self-contained — the model uses it to decide when and how to call the tool.
- Complex parameter types (like `TableDescRequest`) work fine — Sentinel AI introspects the type's
  `@JsonPropertyDescription` annotations to build the parameter schema automatically.
- Keep tools focused: each tool does one thing. The model decides which to call in which order.

To register local tools with the agent after construction:

```java
agent.registerTools(ToolUtils.readTools(new LocalTools(dbPath.toString(), dataDir)));
```

`ToolUtils.readTools()` uses reflection to discover all `@Tool`-annotated methods and wraps them as
`ExecutableTool` instances.

---

## 8. Step 6 — Build the Human-in-the-Loop Tool

A critical feature of this agent is that it asks the user to confirm the generated SQL before
executing it. This is implemented as a `ToolBox` called `AskUserTool`.

```
src/main/java/.../tools/AskUserTool.java
```

```java
@Slf4j
public class AskUserTool implements ToolBox {

    @Override
    public String name() { return "ask_user_tool"; }

    @Tool(
        name = "ask_user_question",
        timeoutSeconds = -1,   // no timeout — blocks until the user types
        value = """
            Ask the user a free-form clarification question and wait for their answer. 
            Use this when the user's intent is ambiguous or a required piece of 
            information is missing.
            """)
    public String askUserQuestion(String question) {
        // print styled banner, read from stdin, return user's answer
    }

    @Tool(
        name = "ask_user_to_choose",
        timeoutSeconds = -1,
        value = """
            Present the user with a numbered list of discrete choices and wait for them to pick one.
            The choices must be provided as a single semicolon-separated string, 
            e.g. "Option A;Option B;Option C".
            """)
    public String askUserToChoose(String question, String choices) {
        // parse choices by ';', print numbered menu, read selection
    }
}
```

**Important:** `timeoutSeconds = -1` disables the default 30-second tool timeout. These tools are
inherently interactive and can block indefinitely waiting for the user.

Register with:

```java
agent.registerToolbox(new AskUserTool());
```

---

## 9. Step 7 — Write the Execution Skill

A **skill** is a Markdown file that the `AgentSkillsExtension` injects into the system prompt as a
secondary task. It encodes the step-by-step execution protocol that the agent must follow on every
query.

Create `src/main/resources/skills/sql-execution/SKILL.md`:

```markdown
---
name: sql-execution
description: >
  Executes SQL queries against the e-commerce SQLite database.
---

# SQL Execution Skill

## Step-by-Step Execution Protocol

### Step 1: Understand User Query & Find Relevant Tables
Call `search_schema` first, then `get_table_desc` on the returned table names.

### Step 2: Resolve Relative Dates
If the user says "today", "last week", etc., call `get_current_dt` and compute
the epoch range for the WHERE clause.

### Step 3: Generate the SQL
- Use table aliases (`o` for `orders`, `u` for `users`, etc.)
- Always qualify columns when joining
- Use CTEs for complex queries
- SELECT only — no INSERT/UPDATE/DELETE
- Strip all `\n`, `\t` from the SQL before passing to execution
- Apply `LIMIT 100` unless the user asks for all rows

### Step 4: Confirm with the user
Use `ask_user_to_choose` to show the SQL and ask for confirmation before executing.

### Step 5: Execute the Query
Use `sqlite-api_execute_query` (HTTP mode) or `execute_query` (MCP mode).

### Step 6: Convert Timestamps
Any column ending in `_at` must be converted via `convert_epoch_to_local_dt`.

### Step 7: Return Structured Result
Return a `SqlQueryResult` with `generatedSql`, `results`, `explanation`, 
and `executionTimeMs`.
```

The skill file is loaded at agent startup by the `AgentSkillsExtension`. The extension reads all
`SKILL.md` files in the skills directory and appends them to the system prompt under a
`<secondary_tasks>` XML section.

---

## 10. Step 8 — Expose the Database via REST (HTTP Toolbox)

The HTTP toolbox approach runs a small Dropwizard REST server in-process and lets the agent call it
via the `sentinel-ai-toolbox-remote-http` module.

### 10.1 JAX-RS Resource

```
src/main/java/.../server/SqliteRestResource.java
```

```java
@Path("/api/sqlite")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class SqliteRestResource {

    private static final Pattern SAFE_IDENTIFIER =
            Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    @POST
    @Path("/query")
    public Response executeQuery(Map<String, Object> body) {
        String sql = (String) body.get("sql");
        // Block write DML
        for (String prefix : Set.of("INSERT","UPDATE","DELETE","DROP","ALTER","CREATE")) {
            if (sql.trim().toUpperCase().startsWith(prefix)) {
                throw new WriteQueryNotAllowedException(...);
            }
        }
        // Execute, time, return SqlQueryResult
    }

    @GET
    @Path("/tables")
    public Response listTables() { ... }

    @GET
    @Path("/schema/{tableName}")
    public Response getTableSchema(@PathParam("tableName") String tableName) { ... }

    @GET
    @Path("/info")
    public Response getDatabaseInfo() { ... }

    // CRUD endpoints: GET/POST/PUT/DELETE /records/{table}
}
```

All identifier inputs (table names, column names) are validated against the `SAFE_IDENTIFIER` regex
before being interpolated into SQL, preventing injection.

### 10.2 HTTP Tool Definitions YAML

Define the tools in `src/main/resources/http-tools/sqlite-api.yml`. The `HttpToolBox` reads this
file and exposes each endpoint as a named tool to the agent:

```yaml
tools:
  - name: execute_query
    description: "Execute a read-only SQL SELECT statement. ..."
    method: POST
    path: /api/sqlite/query
    requestBody:
      schema:
        type: object
        properties:
          sql:
            type: string
            description: "The SQL SELECT statement to execute"
        required: [sql]

  - name: list_tables
    description: "List all user-defined tables in the database."
    method: GET
    path: /api/sqlite/tables
```

### 10.3 Register the HTTP Toolbox

```java
var toolSource = new InMemoryHttpToolSource();
HttpToolReaders.loadToolsFromYAMLContent(
    getClass().getResourceAsStream("/http-tools/sqlite-api.yml").readAllBytes(),
    toolSource);

var httpToolBox = new HttpToolBox(
    "sqlite-api",           // prefix — tool names become "sqlite-api_execute_query", etc.
    new OkHttpClient(),
    toolSource,
    mapper,
    baseUrl);               // e.g. "http://localhost:12345"

agent.registerToolbox(httpToolBox);
```

---

## 11. Step 9 — Alternatively, Expose the Database via MCP

As an alternative to the HTTP toolbox, you can run an MCP server and connect to it using the
`sentinel-ai-toolbox-mcp` module. The agent can use either backend — the only difference is how it
calls the `execute_query` tool.

### 11.1 MCP Server

```
src/main/java/.../mcp/SqliteMcpServer.java
```

```java
@Command(name = "sqlite-mcp-server")
public class SqliteMcpServer implements Callable<Integer> {

    public enum TransportMode { STDIO, SSE }

    @Override
    public Integer call() {
        return switch (transport) {
            case STDIO -> runStdioMode();
            case SSE   -> runSseMode();
        };
    }

    private Integer runStdioMode() {
        var transportProvider = new StdioServerTransportProvider(jsonMapper);
        McpServer.sync(transportProvider)
            .toolCall(executeQueryTool, (exchange, args) -> handleExecuteQuery(args, mapper))
            .toolCall(listTablesTool,   (exchange, args) -> handleListTables(mapper))
            .toolCall(getTableSchemaTool, ...)
            .toolCall(getDatabaseInfoTool, ...)
            .build();
        // block main thread until stdin closes
        new CountDownLatch(1).await();
        return 0;
    }
}
```

**STDIO mode important note:** The MCP protocol uses stdout for JSON-RPC messages. Redirect all
logging to stderr in `main()`:

```java
public static void main(String[] args) {
    redirectLoggingToStderr();   // critical — log lines on stdout corrupt MCP framing
    System.exit(new CommandLine(new SqliteMcpServer()).execute(args));
}
```

### 11.2 Register the MCP Toolbox (STDIO)

```java
var mcpConfig = MCPStdioServerConfig.builder()
    .command(javaCmd)
    .args(List.of(
        "-cp", System.getProperty("java.class.path"),
        "com.phonepe.sentinelai.examples.texttosql.mcp.SqliteMcpServer",
        "--db-path", dbPath.toString()))
    .build();

var mcpToolBox = MCPToolBox.buildFromConfig()
    .name("sqlite-mcp")
    .mapper(mapper)
    .mcpServerConfig(mcpConfig)
    .build();

agent.registerToolbox(mcpToolBox);
```

### 11.3 Register the MCP Toolbox (SSE)

```java
var mcpConfig = MCPSSEServerConfig.builder()
    .url("http://localhost:" + port)
    .build();

var mcpToolBox = MCPToolBox.buildFromConfig()
    .name("sqlite-mcp")
    .mapper(mapper)
    .mcpServerConfig(mcpConfig)
    .build();

agent.registerToolbox(mcpToolBox);
```

---

## 12. Step 10 — Implement the Agent

With all tools and skills in place, the agent itself is remarkably short. It extends
`Agent<String, SqlQueryResult, TextToSqlAgent>` where:

- `String` — input type (the user's natural-language question)
- `SqlQueryResult` — output type (the structured result)
- `TextToSqlAgent` — the agent's own type (enables fluent builder chaining)

```
src/main/java/.../agent/TextToSqlAgent.java
```

```java
public class TextToSqlAgent extends Agent<String, SqlQueryResult, TextToSqlAgent> {

    private static final String SYSTEM_PROMPT =
            """
            You are an expert SQL assistant for an e-commerce SQLite database.
            Translate natural-language questions into SQL queries, execute them,
            and return structured results.
            Follow the sql-execution skill protocol for every request.
            If unable to proceed without user input, ask the user for clarification.
            The user's timezone is '%s'. Use this for all date formatting and conversions.
            """.formatted(TimeZone.getDefault().toZoneId().toString());

    @Builder
    public TextToSqlAgent(
            @NonNull AgentSetup setup,
            @Singular List<AgentExtension<String, SqlQueryResult, TextToSqlAgent>> extensions,
            @NonNull OutputValidator<String, SqlQueryResult> outputValidator) {
        super(
            SqlQueryResult.class,
            SYSTEM_PROMPT,
            setup,
            extensions,
            Map.of(),                       // no built-in tools; registered externally
            new ApproveAllToolRuns<>(),      // auto-approve all tool calls
            outputValidator,
            new DefaultErrorHandler<>(),
            new NeverTerminateEarlyStrategy()); // always run to completion
    }

    @Override
    public String name() { return "text-to-sql-agent"; }
}
```

**Construction notes:**

- `ApproveAllToolRuns` — auto-approves every tool call the model makes. Replace with a custom
  `ToolRunApprover` if you need to intercept or log approvals.
- `NeverTerminateEarlyStrategy` — the agent never gives up early, even if intermediate tool results
  look empty. This is important for multi-step queries.
- `DefaultErrorHandler` — surfaces errors from tool calls back to the model as tool results, giving
  the model a chance to retry or explain the error.
- `extensions` — the `AgentSkillsExtension` is passed here. It hooks into the system prompt
  construction phase and appends the skill as a `<secondary_tasks>` section.

---

## 13. Step 11 — Wire Everything Together in the CLI

The `TextToSqlCLI` class orchestrates the full startup sequence using PicoCLI:

```
src/main/java/.../cli/TextToSqlCLI.java
```

### Startup sequence

```java
@Command(name = "text-to-sql")
public class TextToSqlCLI implements Callable<Integer> {

    @Option(names = {"--config", "-c"}, defaultValue = ".env/agent-config.yml")
    private String configPath;

    @Option(names = {"--toolbox-mode", "-t"}, defaultValue = "HTTP")
    private ToolboxMode toolboxMode;   // HTTP or MCP

    @Option(names = {"--mcp-server-mode"}, defaultValue = "STDIO")
    private McpServerMode mcpServerMode;   // STDIO or SSE

    @Override
    public Integer call() throws Exception {
        // 1. Load YAML config
        CliConfig config = loadConfig(configPath);

        // 2. Initialise SQLite database (schema + seed data)
        Path dbPath = initializeDatabase(config);

        // 3. Build OkHttpClient with API key interceptor
        OkHttpClientAdapter clientAdapter = buildTrustedHttpClient(config);

        // 4. Build OpenAI model
        SimpleOpenAIModel<?> model = buildOpenAIModel(config, clientAdapter, mapper);

        // 5. Build AgentSetup (temperature, maxTokens, output mode)
        AgentSetup agentSetup = AgentSetup.builder()
            .mapper(mapper)
            .model(model)
            .modelSettings(ModelSettings.builder()
                .temperature(config.getAgent().getTemperature())
                .maxTokens(config.getAgent().getMaxTokens())
                .build())
            .outputGenerationMode(OutputGenerationMode.TOOL_BASED)
            .outputGenerationTool(result -> result)
            .build();

        // 6. Build skills extension (extracts bundled SKILL.md to a temp dir)
        AgentSkillsExtension<...> skillsExtension = AgentSkillsExtension
            .withMultipleSkills()
            .baseDir(".")
            .skillsDirectories(List.of(resolvedSkillsDir))
            .build();

        // 7. Build agent
        TextToSqlAgent agent = TextToSqlAgent.builder()
            .setup(agentSetup)
            .extension(skillsExtension)
            .outputValidator((ctx, out) -> OutputValidationResults.success())
            .build();

        // 8. Register local tools (vector store, schema, JDBC)
        agent.registerTools(ToolUtils.readTools(new LocalTools(dbPath.toString(), dbPath.getParent())));

        // 9. Register ask-user tool
        agent.registerToolbox(new AskUserTool());

        // 10. Register SQL execution toolbox (HTTP or MCP)
        if (toolboxMode == ToolboxMode.MCP) {
            registerMcpToolbox(agent, dbPath, mapper);   // or SSE variant
        } else {
            String baseUrl = startRestServer(dbPath, mapper);
            registerHttpToolbox(agent, baseUrl, mapper);
        }

        // 11. Run interactive REPL
        return runInteractiveLoop(agent, config, sessionId, mapper);
    }
}
```

### Configuration file

Copy `src/main/resources/.env/agent-config.yml.example` to `.env/agent-config.yml` and fill in
your API key:

```yaml
openai:
  apiKey: "sk-your-api-key-here"
  model: "gpt-4o"
  bearerPrefix: "Bearer "

database:
  path: "./ecommerce.db"

agent:
  temperature: 0.0      # deterministic SQL generation
  maxTokens: 4096
  streaming: true       # stream assistant tokens to stdout
```

### Interactive REPL

The CLI runs a read-eval-print loop. Each user question is dispatched to `agent.executeAsync()` or
`agent.executeAsyncStreaming()` based on the `streaming` config flag:

```java
AgentInput<String> input = AgentInput.<String>builder()
    .request(question)
    .requestMetadata(AgentRequestMetadata.builder()
        .sessionId(sessionId)
        .userId("cli-user")
        .build())
    .build();

// Streaming mode — tokens arrive token-by-token
CompletableFuture<AgentOutput<SqlQueryResult>> future =
    agent.executeAsyncStreaming(input, chunk -> System.out.print(new String(chunk)));

// Or non-streaming
CompletableFuture<AgentOutput<SqlQueryResult>> future = agent.executeAsync(input);
```

The session ID ensures conversation history is maintained across turns — the agent remembers
previous questions and results within the same session.

Special REPL commands:
- `exit` / `quit` — terminates the session
- `/dumpMessages [file]` — serialises the full message history to JSON for debugging

---

## 14. Step 12 — Run the Agent

### Build the fat JAR

```bash
mvn clean package -pl sentinel-ai-examples -am -DskipTests
```

This produces `target/sentinel-ai-examples-*-cli.jar` (a shaded JAR with all dependencies).

### Set up credentials

```bash
mkdir -p sentinel-ai-examples/.env
cp sentinel-ai-examples/src/main/resources/.env/agent-config.yml.example \
   sentinel-ai-examples/.env/agent-config.yml
# Edit agent-config.yml and add your OpenAI API key
```

### Launch (HTTP toolbox — default)

```bash
java -jar sentinel-ai-examples/target/sentinel-ai-examples-*-cli.jar \
     --config sentinel-ai-examples/.env/agent-config.yml
```

### Launch (MCP toolbox — stdio transport)

```bash
java -jar sentinel-ai-examples/target/sentinel-ai-examples-*-cli.jar \
     --config sentinel-ai-examples/.env/agent-config.yml \
     --toolbox-mode MCP
```

### Launch (MCP toolbox — SSE transport)

```bash
java -jar sentinel-ai-examples/target/sentinel-ai-examples-*-cli.jar \
     --config sentinel-ai-examples/.env/agent-config.yml \
     --toolbox-mode MCP \
     --mcp-server-mode SSE \
     --mcp-port 8766
```

### Example queries to try

```
> How many orders are in each status?
> Who are the top 5 sellers by revenue from delivered orders?
> Show me all products that are below reorder level in any warehouse
> What orders did users from Bangalore place in the last 30 days?
> Which product category generates the most revenue?
```

The agent will:
1. Search the schema for relevant tables/columns
2. Resolve any relative dates
3. Generate and pretty-print the SQL
4. Ask you to confirm before executing
5. Execute the query and convert any epoch timestamps
6. Return a `SqlQueryResult` with the results, SQL, and explanation

---

## 15. Project Layout Reference

```
sentinel-ai-examples/
├── pom.xml
└── src/main/
    ├── java/.../texttosql/
    │   ├── agent/
    │   │   └── TextToSqlAgent.java          ← The agent (Step 10)
    │   ├── cli/
    │   │   ├── CliConfig.java               ← YAML config POJO
    │   │   ├── ConsoleUtils.java            ← Terminal formatting helpers
    │   │   └── TextToSqlCLI.java            ← Entry point + REPL (Step 11)
    │   ├── mcp/
    │   │   └── SqliteMcpServer.java         ← MCP server subprocess (Step 9)
    │   ├── server/
    │   │   ├── SqliteRestResource.java      ← JAX-RS resource (Step 8)
    │   │   └── SqliteRestServer.java        ← Dropwizard bootstrap
    │   └── tools/
    │       ├── AskUserTool.java             ← Human-in-the-loop toolbox (Step 6)
    │       ├── DatabaseInitializer.java     ← DB bootstrap utility (Step 3)
    │       ├── LocalTools.java              ← Local toolbox (Step 5)
    │       ├── model/
    │       │   ├── SqlQueryResult.java      ← Output record (Step 1)
    │       │   └── TableDescRequest.java    ← Tool parameter type
    │       └── vectorstore/
    │           ├── HashTextEmbedder.java    ← Feature-hashing embedder (Step 4)
    │           ├── SchemaSearchResult.java  ← Search result record
    │           ├── SchemaVectorStore.java   ← Lucene hybrid search (Step 4)
    │           └── VectorStoreInitializer.java
    └── resources/
        ├── .env/
        │   └── agent-config.yml.example    ← Config template (Step 11)
        ├── db/
        │   ├── schema.sql                  ← DDL (Step 2)
        │   ├── schema_descriptions.json    ← Semantic descriptions (Step 4)
        │   └── ecommerce-data/
        │       ├── users.csv               ← Seed data (Step 3)
        │       ├── sellers.csv
        │       ├── catalog.csv
        │       ├── inventory.csv
        │       └── orders.csv
        ├── http-tools/
        │   └── sqlite-api.yml              ← HTTP tool definitions (Step 8)
        └── skills/sql-execution/
            └── SKILL.md                    ← Execution protocol (Step 7)
```
