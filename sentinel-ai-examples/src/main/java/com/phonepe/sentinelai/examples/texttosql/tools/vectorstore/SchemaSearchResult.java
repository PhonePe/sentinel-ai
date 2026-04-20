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

package com.phonepe.sentinelai.examples.texttosql.tools.vectorstore;

/**
 * A single result returned by {@link SchemaVectorStore#hybridSearch}.
 *
 * @param docType "table" when the match is a table-level document, "column" when it is a
 *     column-level document
 * @param tableName name of the matching table
 * @param columnName name of the matching column, or {@code null} for table-level documents
 * @param content full text content that was indexed for this document
 * @param score combined hybrid score (BM25 + cosine similarity), higher is better
 */
public record SchemaSearchResult(
        String docType, String tableName, String columnName, String content, float score) {}
