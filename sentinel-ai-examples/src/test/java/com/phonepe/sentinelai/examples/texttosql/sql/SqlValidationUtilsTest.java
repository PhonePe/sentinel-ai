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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlValidationUtilsTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "analytics", "main_db", "warehouse_01"
    })
    void acceptsValidDatabaseNames(String databaseName) {
        assertDoesNotThrow(() -> SqlValidationUtils.validateDatabaseName(databaseName));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "users", "orders_2025", "inventory_archive"
    })
    void acceptsValidTableNames(String tableName) {
        assertDoesNotThrow(() -> SqlValidationUtils.validateTableName(tableName));
    }

    @Test
    void findsDisallowedWriteKeywordInSql() {
        assertEquals("INSERT", SqlValidationUtils.findDisallowedWriteKeyword("INSERT INTO users(id) VALUES(1)"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "bad db", "../prod", "1primary"
    })
    void rejectsInvalidDatabaseNames(String databaseName) {
        final var exception = assertThrows(
                                           IllegalArgumentException.class,
                                           () -> SqlValidationUtils.validateDatabaseName(
                                                                                         databaseName));
        assertEquals("Invalid database name: " + databaseName, exception.getMessage());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "", "bad table", "users;DROP", "123users", "../users"
    })
    void rejectsInvalidTableNames(String tableName) {
        final var exception = assertThrows(
                                           IllegalArgumentException.class,
                                           () -> SqlValidationUtils.validateTableName(tableName));
        assertEquals("Invalid table name: " + tableName, exception.getMessage());
    }

    @ParameterizedTest
    @NullSource
    void rejectsNullDatabaseNames(String databaseName) {
        final var exception = assertThrows(
                                           IllegalArgumentException.class,
                                           () -> SqlValidationUtils.validateDatabaseName(
                                                                                         databaseName));
        assertEquals("Invalid database name: null", exception.getMessage());
    }

    @Test
    void returnsNullWhenSqlHasNoDisallowedWriteKeyword() {
        assertNull(SqlValidationUtils.findDisallowedWriteKeyword("WITH cte AS (SELECT 1) SELECT * FROM cte"));
        assertNull(SqlValidationUtils.findDisallowedWriteKeyword("PRAGMA table_info(users)"));
        assertNull(SqlValidationUtils.findDisallowedWriteKeyword(null));
    }
}
