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

import java.util.List;
import java.util.Map;

/**
 * Structured output produced by the {@link TextToSqlAgent} for every user query.
 *
 * <p>The agent always populates all fields, even when a query fails to execute
 * (in which case {@code results} will be empty and {@code explanation} will
 * describe the error).
 *
 * @param generatedSql    The SQL statement that was generated from the natural-language request.
 * @param results         Rows returned by the query. Each row is a map of column-name → value.
 *                        Empty list if the query returned no rows or could not be executed.
 * @param explanation     A human-readable summary of what was done, what the results mean,
 *                        and any caveats or errors encountered.
 * @param executionTimeMs Wall-clock time in milliseconds from query submission to result receipt.
 */
public record SqlQueryResult(
        String generatedSql,
        List<Map<String, Object>> results,
        String explanation,
        long executionTimeMs
) {
}
