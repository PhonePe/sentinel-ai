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

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Structured output produced by the {@link TextToSqlAgent} for every user query.
 *
*/
@JsonClassDescription("Result of executing a SQL query against a SQLite database. Contains the generated SQL, " +
        "the query results (if any), an explanation of the results or any errors, and the execution time.")
public record SqlQueryResult(
        @JsonPropertyDescription("The SQL statement that was generated from the user provided natural-language request")
        String generatedSql,

        @JsonPropertyDescription("Rows returned by the query. Each entry is a JSON string representing one row, " +
                "with the format {\"col1Name\": col1Value, \"col2Name\": col2Value, ...} where values can be any " +
                "JSON type. Empty list if the query returned no rows or could not be executed.")
        List<String> results,

        @JsonPropertyDescription("A human-readable summary of what was done, what the results mean," +
                " and any caveats or errors encountered.")
        String explanation,

        @JsonPropertyDescription("Wall-clock time in milliseconds from query submission to result receipt.")
        long executionTimeMs
) {
}
