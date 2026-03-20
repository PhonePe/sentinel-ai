---
name: sql-execution
description: >
  Executes SQL queries against the e-commerce SQLite database using three
  complementary paths: the MCP SQLite server (primary), the remote-HTTP
  SQLite API (fallback/validation), and local Java tools (schema inspection,
  timestamp conversion, result formatting). Activate this skill when the user
  explicitly asks to run, execute, or query the database.
license: Apache-2.0
compatibility: Requires mcp-sqlite MCP server (npx -y mcp-sqlite <db-path>) and the embedded SQLite REST API server.
---

# SQL Execution Skill

## Overview

This skill enables the agent to translate natural-language questions into
precise SQL queries and execute them against the e-commerce SQLite database.
It orchestrates three types of tools:

1. **Local tools** — in-process Java methods for schema inspection, timestamp
   conversion, and result formatting.
2. **MCP SQLite tools** — provided by the `mcp-sqlite` Node.js MCP server
   (primary query execution path).
3. **Remote-HTTP tools** — provided by the embedded Dropwizard SQLite REST API
   (fallback / cross-validation path).

---

## When to Activate This Skill

Activate this skill when the user says anything like:

- "Execute this query…"
- "Run a query to find…"
- "Query the database for…"
- "Show me the SQL for…"
- "How many orders are pending?"
- "What is the top-selling product?"

Do NOT activate this skill for purely conversational questions about the schema
or general SQL syntax — answer those directly.

---

## Step-by-Step Execution Protocol

### Step 1: Understand the Schema

**Always call `getDatabaseSchema` (local tool) first.**

This returns the complete DDL with column-level comments for all five tables:
`users`, `sellers`, `catalog`, `inventory`, `orders`.

Key schema facts to remember:
- All `*_at` columns (e.g. `ordered_at`, `created_at`, `delivered_at`) store
  **Unix epoch seconds** (INTEGER). Always convert them for display.
- `orders.total_amount = orders.quantity × orders.unit_price` (pre-computed).
- `orders.status` lifecycle: `pending → confirmed → shipped → delivered`
  (or `cancelled`).
- Inventory is split per warehouse. Total stock = `SUM(quantity)` grouped by
  `product_id`.
- Table relationships:
  - `orders.user_id    → users.user_id`
  - `orders.product_id → catalog.product_id`
  - `orders.seller_id  → sellers.seller_id`
  - `catalog.seller_id → sellers.seller_id`
  - `inventory.product_id → catalog.product_id`

Optionally call `getTableRowCounts` (local tool) to understand data volume.

---

### Step 2: Resolve Relative Dates

If the user mentions relative time periods ("today", "this week", "last month",
"in January", "past 30 days"), call `getCurrentDateTime` (local tool) to get
the current epoch time in the user's timezone, then compute the epoch range for
the WHERE clause.

Example:
- "Orders from last 7 days" → `ordered_at >= (now - 7*86400)`

---

### Step 3: Generate the SQL

Write a clear, correct SQL query:

- Use table aliases for readability (e.g. `o` for `orders`, `u` for `users`).
- Always qualify column names with the alias when joining multiple tables.
- For aggregations, use CTEs (`WITH`) to make the query readable.
- Prefer `INNER JOIN` for required relationships, `LEFT JOIN` for optional ones.
- For ranking (e.g. "top 5 products"), use `ORDER BY ... LIMIT 5`.
- Never use `SELECT *` in queries that will be shown to the user — be explicit
  about which columns you return.

---

### Step 4: Execute the Query (Primary Path — MCP SQLite Server)

Use the `mcp-sqlite_query` tool to execute the SQL. This is the primary
execution path backed by the `mcp-sqlite` npm MCP server running via stdio.

```
Tool: mcp-sqlite_query
Arguments: {"sql": "<your generated SQL>"}
```

The result will contain a `rows` array of objects and a `rowCount`.

If you need to inspect the schema interactively, you can also use:
- `mcp-sqlite_list_tables` — list all tables
- `mcp-sqlite_get_table_schema` — get column definitions for a table

---

### Step 5: Fallback / Validation (Remote-HTTP SQLite API)

If the MCP path fails (e.g. `npx` is not available, the server did not start,
or the tool returns an error), **fall back to the remote-HTTP tools**:

```
Tool: sqlite-api_executeQuery
Arguments: {"sql": "<your generated SQL>"}
```

You can also use the HTTP tools to cross-validate results from the MCP path,
or to perform simple CRUD operations:
- `sqlite-api_listTables`
- `sqlite-api_getTableSchema` (with `tableName` parameter)
- `sqlite-api_readRecords` (with `tableName` and optional `conditions`)

---

### Step 6: Process Timestamps

After receiving query results, inspect every value whose column name ends in
`_at`. Convert each such value using the `convertEpochToLocalDateTime` local
tool:

```
Tool: convertEpochToLocalDateTime
Arguments: {"epochSeconds": <value>, "timezone": "<user-timezone>"}
```

If you do not know the user's timezone, assume `"Asia/Kolkata"` (IST) as a
sensible default for this dataset and mention the assumption in your explanation.

---

### Step 7: Format and Present Results

1. Call `formatResultsAsTable` (local tool) on the `rows` array to produce a
   clean Markdown table.
2. Compose a final response that includes:
   - The generated SQL in a fenced code block.
   - The formatted Markdown table of results.
   - A plain-English explanation of what the query found.
   - The total row count.
   - Any caveats (e.g. NULLs, approximate counts, timezone assumptions).

---

## Reference: Tool Inventory

| Tool | Type | Purpose |
|---|---|---|
| `getDatabaseSchema` | Local | Full schema with DDL and column comments |
| `getTableRowCounts` | Local | Row counts per table |
| `getCurrentDateTime` | Local | Current epoch time in given timezone |
| `convertEpochToLocalDateTime` | Local | Epoch → yyyy/MM/dd HH:mm:ss |
| `formatResultsAsTable` | Local | JSON rows → Markdown table |
| `mcp-sqlite_query` | MCP | Execute arbitrary SQL (primary path) |
| `mcp-sqlite_list_tables` | MCP | List all tables |
| `mcp-sqlite_get_table_schema` | MCP | Get column definitions for a table |
| `sqlite-api_executeQuery` | Remote-HTTP | Execute arbitrary SQL (fallback path) |
| `sqlite-api_listTables` | Remote-HTTP | List all tables |
| `sqlite-api_getTableSchema` | Remote-HTTP | Get column definitions |
| `sqlite-api_readRecords` | Remote-HTTP | Read records with simple filters |
| `sqlite-api_insertRecord` | Remote-HTTP | Insert a new record |
| `sqlite-api_updateRecords` | Remote-HTTP | Update matching records |

---

## Common Query Patterns

### Revenue analysis
```sql
SELECT
    s.seller_name,
    COUNT(o.order_id)   AS total_orders,
    SUM(o.total_amount) AS total_revenue
FROM orders o
JOIN sellers s ON o.seller_id = s.seller_id
WHERE o.status = 'delivered'
GROUP BY s.seller_id, s.seller_name
ORDER BY total_revenue DESC;
```

### Inventory at risk (below reorder level)
```sql
SELECT
    c.product_name,
    i.warehouse,
    i.quantity,
    i.reorder_level
FROM inventory i
JOIN catalog c ON i.product_id = c.product_id
WHERE i.quantity <= i.reorder_level
ORDER BY i.quantity ASC;
```

### User purchase history
```sql
SELECT
    o.order_id,
    c.product_name,
    o.quantity,
    o.total_amount,
    o.status,
    o.ordered_at  -- remember to convert this with convertEpochToLocalDateTime
FROM orders o
JOIN catalog c ON o.product_id = c.product_id
WHERE o.user_id = ?
ORDER BY o.ordered_at DESC;
```
