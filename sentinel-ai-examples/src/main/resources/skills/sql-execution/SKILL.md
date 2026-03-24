---
name: sql-execution
description: >
  Executes SQL queries against the e-commerce SQLite database using two
  complementary paths: the remote-HTTP SQLite API (primary query execution)
  and local Java tools (schema inspection, timestamp conversion, result
  formatting). Activate this skill when the user asks to run, execute, or
  query the database.
license: Apache-2.0
compatibility: Requires the embedded SQLite REST API server started by the CLI at launch.
---

# SQL Execution Skill

## Overview

This skill enables the agent to translate natural-language questions into
precise SQL queries and execute them against the e-commerce SQLite database.
It uses two types of tools:

1. **Local tools** — in-process Java methods for schema inspection, timestamp
   conversion, and result formatting (registered via `LocalSqlTools`).
2. **Remote-HTTP tools** — HTTP calls to the embedded Dropwizard SQLite REST
   API (registered via `HttpToolBox` with the `sqlite-api` prefix).

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

### Step 1: Understand User Query & Find Relevant Tables

**Call `search_schema` first** with a natural-language description of what the user is asking for.

The results return a ranked list of relevant tables and columns. Extract the unique table names from
this list, then call `get_table_desc` with those table names to get their full descriptions
(columns, data types, nullability, and semantic meaning).

Use this information to:
- Identify which tables and columns to query
- Understand column data types and constraints
- Know the semantic meaning of each field before writing SQL

Key schema facts that apply across all queries:
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

Optionally call `get_table_row_counts` (local tool) to understand data volume.

---

### Step 2: Resolve Relative Dates

If the user mentions relative time periods ("today", "this week", "last month",
"in January", "past 30 days"), call `get_current_dt` (local tool) to get
the current epoch time in the user's timezone, then compute the epoch range for
the WHERE clause.

Example:
- "Orders from last 7 days" → `ordered_at >= (now_epoch - 7 * 86400)`

---

### Step 3: Generate the SQL

Write a clear, correct SQL query:

- Use table aliases for readability (e.g. `o` for `orders`, `u` for `users`).
- Always qualify column names with the alias when joining multiple tables.
- For aggregations, use CTEs (`WITH`) to make the query readable.
- Prefer `INNER JOIN` for required relationships, `LEFT JOIN` for optional ones.
- For ranking queries (e.g. "top 5 products"), use `ORDER BY <column> DESC LIMIT 5`.
- Never use `SELECT *` — be explicit about which columns you return.
- Strip all `\n`, `\t`, `\r` characters from the generated SQL string before
  passing it to the execution tool. The generated sql query need not be pretty-formatted. 
  It should only be syntactically and semantically correct.
- Use only READ-ONLY queries (SELECT). INSERT, UPDATE, DELETE, DROP, TRUNCATE
  are not permitted.
- Apply `LIMIT 100` for queries that might return large result sets, unless the
  user explicitly asks for all rows.
- If the question is ambiguous, make a reasonable assumption and state it clearly
  in the explanation field.
- If no rows are found, return an empty results list and explain why in the
  explanation.

---

### Step 4: Execute the Query

Use the `sqlite-api_execute_query` tool to run the SQL against the database.

```
Tool: sqlite-api_execute_query
Arguments: {"sql": "<your generated SQL>"}
```

See sample JSON document for the query result set for a SELECT query:

```json
{
  "generatedSql": "SELECT o.order_id, u.full_name FROM orders o JOIN users u ON o.user_id = u.user_id WHERE o.status = 'pending' LIMIT 100",
  "results": [
    "{\"column1\": value1, \"column2\": value2}",
    "{\"column2\": value2, \"column3\": value3}"
  ],
  "executionTimeMs": 115
}
```

If you need to cross-check available tables or inspect a specific table's
columns before writing the query, you can use:
- `sqlite-api_list_tables` — list all tables in the database
- `sqlite-api_get_table_schema` — column definitions for a named table
- `sqlite-api_get_database_info` — high-level database metadata

---

### Step 5: Process Timestamps

After receiving query results, inspect every value whose column name ends in
`_at`. Convert each such value using the `convert_epoch_to_local_dt` local tool:

```
Tool: convert_epoch_to_local_dt
Arguments: {"epochSeconds": <value>, "timezone": "<user-timezone>"}
```

If you do not know the user's timezone, assume `"Asia/Kolkata"` (IST) and
mention the assumption in your explanation.

---

### Step 6: Format and Present Results

1. Return the result as a json following schema of type (SqlQueryResult).
2. Fill the explanation field in the json with a plain-English description of the query along with assumptions and 
   caveats (e.g. NULLs, approximate counts, etc)

---

## Reference: Tool Inventory

| Tool | Type | Purpose                                                       |
|---|---|---------------------------------------------------------------|
| `search_schema` | Local | Hybrid keyword+semantic search to find relevant tables/columns |
| `get_table_desc` | Local | Full description (columns, types, semantics) for a list of tables |
| `get_column_desc` | Local | Description of a specific column in a table                   |
| `get_table_row_counts` | Local | Row counts per table                                          |
| `get_current_dt` | Local | Current epoch time in a given IANA timezone                   |
| `convert_epoch_to_local_dt` | Local | Epoch seconds → `yyyy/MM/dd HH:mm:ss`                         |
| `format_results_as_table` | Local | Render a `SqlQueryResult` as an ASCII table                   |
| `sqlite-api_execute_query` | Remote-HTTP | Execute arbitrary SQL (primary execution path)      |
| `sqlite-api_list_tables` | Remote-HTTP | List all tables in the database                     |
| `sqlite-api_get_table_schema` | Remote-HTTP | Column definitions for a named table                |
| `sqlite-api_get_database_info` | Remote-HTTP | High-level database metadata                        |
| `sqlite-api_read_records` | Remote-HTTP | Read rows with simple equality filters              |
| `sqlite-api_insert_record` | Remote-HTTP | Insert a new record into a table                    |
| `sqlite-api_update_records` | Remote-HTTP | Update rows matching given conditions               |

---

## Common Query Patterns

### Top sellers by order volume
```sql
SELECT s.seller_name, COUNT(o.order_id) AS total_orders
FROM orders o
JOIN sellers s ON o.seller_id = s.seller_id
WHERE o.status = 'delivered'
GROUP BY s.seller_id, s.seller_name
ORDER BY total_orders DESC
LIMIT 10;
```

### Revenue analysis
```sql
SELECT s.seller_name, COUNT(o.order_id) AS total_orders, SUM(o.total_amount) AS total_revenue
FROM orders o
JOIN sellers s ON o.seller_id = s.seller_id
WHERE o.status = 'delivered'
GROUP BY s.seller_id, s.seller_name
ORDER BY total_revenue DESC;
```

### User with most orders
```sql
SELECT u.full_name, u.email, COUNT(o.order_id) AS order_count
FROM orders o
JOIN users u ON o.user_id = u.user_id
GROUP BY o.user_id, u.full_name, u.email
ORDER BY order_count DESC
LIMIT 1;
```

### Top cities by product category sales
```sql
SELECT u.city, c.category, SUM(o.total_amount) AS revenue
FROM orders o
JOIN users u ON o.user_id = u.user_id
JOIN catalog c ON o.product_id = c.product_id
WHERE o.status = 'delivered'
GROUP BY u.city, c.category
ORDER BY revenue DESC
LIMIT 20;
```

### Inventory at risk (below reorder level)
```sql
SELECT c.product_name, i.warehouse, i.quantity, i.reorder_level
FROM inventory i
JOIN catalog c ON i.product_id = c.product_id
WHERE i.quantity <= i.reorder_level
ORDER BY i.quantity ASC;
```

### User purchase history
```sql
SELECT o.order_id, c.product_name, o.quantity, o.total_amount, o.status,
       o.ordered_at  -- remember to convert with convert_epoch_to_local_dt
FROM orders o
JOIN catalog c ON o.product_id = c.product_id
WHERE o.user_id = ?
ORDER BY o.ordered_at DESC;
```
