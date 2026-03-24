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
import java.util.TimeZone;

import com.phonepe.sentinelai.examples.texttosql.tools.model.SqlQueryResult;
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
            You are an expert SQL assistant for an e-commerce SQLite database.
            Translate natural-language questions into SQL queries, execute them, and return structured results.
            Follow the sql-execution skill protocol for every request.
            If unable to proceed without user input, ask the user for clarification before continuing.
            The user's timezone is '%s'. Use this timezone for all date formatting and timestamp conversions.
            """.formatted(TimeZone.getDefault().toZoneId().toString());

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
