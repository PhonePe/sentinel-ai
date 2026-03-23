/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.sentinelai.examples.texttosql.agent;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.ApproveAllToolRuns;
import com.phonepe.sentinelai.core.earlytermination.NeverTerminateEarlyStrategy;
import com.phonepe.sentinelai.core.errorhandling.DefaultErrorHandler;
import com.phonepe.sentinelai.core.outputvalidation.OutputValidator;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;

/**
 * Text-to-SQL agent that translates natural-language questions into SQLite queries and returns
 * structured results.
 *
 * <p>The agent is equipped with three layers of tools:
 *
 * <ol>
 *   <li><b>Local tools</b> — registered via {@code registerTools()} after construction; these
 *       include timestamp conversion, schema inspection, and result formatting ({@link
 *       com.phonepe.sentinelai.examples.texttosql.tools.LocalSqlTools}).
 *   <li><b>Remote-HTTP toolbox</b> — registered via {@code registerToolbox()} after construction;
 *       these call the embedded Dropwizard SQLite REST server.
 *   <li><b>Skills extension</b> — injected via {@code extensions} at construction time; provides
 *       the SQL execution skill loaded from {@code resources/skills/sql-execution/}.
 * </ol>
 *
 * <p>Output type is {@link SqlQueryResult}, a record holding the generated SQL, result rows, a
 * human-readable explanation, and wall-clock execution time.
 */
public class TextToSqlAgent extends Agent<String, SqlQueryResult, TextToSqlAgent> {

    private static final String SYSTEM_PROMPT =
            """
            You are an expert SQL assistant for an e-commerce platform.
            Your job is to translate the user's natural-language question into a correct
            SQLite query, execute it using the available tools, and return a structured result.

            Database: SQLite, e-commerce (users, sellers, catalog, inventory, orders).
            All *_at columns store Unix epoch seconds — always convert them before displaying.

            Mandatory workflow for every question:
            1. Use get_db_schema tool to understand the data model (only needed once per session).
            2. Analyse the user's question and identify the relevant tables and columns.
            3. Compose a valid SQLite SELECT (or other DML) statement.
            4. Execute the query using the execute_query tool (remote-HTTP toolbox) or the
               mcp-sqlite 'query' tool if available. When the query executes successfully, it would return
               the result set as a json which follows the schema of SqlQueryResult.
            5. If there are any timestamp columns in the result, then convert all *_at timestamp column values
               to human-readable format via convert_epoch_to_local_dt tool on each row in result set.
            6. Finally call the output generator tool to display the result set json (type: SqlQueryResult) as an ASCII table.

            Always:
            - The generated sql query need not be pretty. So remove '\\n', '\\t', '\\r' characters from the generated query.
            - Use table aliases and explicit column lists in complex JOINs.
            - Apply LIMIT 100 for queries that might return large result sets, unless the user
              asks for all rows.
            - Use only READ-ONLY queries (SELECT). Writes of any kind (INSERTS, DELETES, UPDATES, TRUNCATE, DROP, etc.) are not permitted.
            - If the question is ambiguous, make a reasonable assumption and state it in the
              explanation field. If unable to proceed without user input, ask for the user's input.
            - If no rows are found, return an empty results list and explain why in the explanation.
            """;

    /**
     * Constructs the agent.
     *
     * @param setup agent setup (model, mapper, model settings)
     * @param extensions agent extensions, e.g. {@code AgentSkillsExtension}
     */
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
                Map.of(),
                new ApproveAllToolRuns<>(),
                outputValidator,
                new DefaultErrorHandler<>(),
                new NeverTerminateEarlyStrategy());
    }

    @Override
    public String name() {
        return "text-to-sql-agent";
    }
}
