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

package com.phonepe.sentinelai.examples.texttosql.sql;

import java.util.Set;
import java.util.regex.Pattern;

/** Shared validation helpers for SQL identifiers accepted by the text-to-SQL examples. */
public final class SqlValidationUtils {

    private static final String DATABASE_NAME_LABEL = "database name";
    private static final String TABLE_NAME_LABEL = "table name";
    private static final Set<String> DISALLOWED_WRITE_KEYWORDS = Set.of(
                                                                        "INSERT",
                                                                        "UPDATE",
                                                                        "DELETE",
                                                                        "DROP",
                                                                        "ALTER",
                                                                        "CREATE",
                                                                        "REPLACE",
                                                                        "TRUNCATE",
                                                                        "MERGE",
                                                                        "ATTACH",
                                                                        "DETACH");

    /** Only allow identifiers that are alphanumeric plus underscores to prevent SQL injection. */
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_]\\w*$");

    private SqlValidationUtils() {
    }

    public static String findDisallowedWriteKeyword(String sql) {
        if (sql == null) {
            return null;
        }
        final var upper = sql.trim().toUpperCase();
        for (final String keyword : DISALLOWED_WRITE_KEYWORDS) {
            if (upper.contains(keyword)) {
                return keyword;
            }
        }
        return null;
    }

    @SuppressWarnings("unused")
    public static void validateDatabaseName(String databaseName) {
        validateIdentifier(databaseName, DATABASE_NAME_LABEL);
    }

    public static void validateIdentifier(String identifier, String label) {
        if (identifier == null || !SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Invalid " + label + ": " + identifier);
        }
    }

    public static void validateTableName(String tableName) {
        validateIdentifier(tableName, TABLE_NAME_LABEL);
    }
}
