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

package com.phonepe.sentinelai.storage.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.session.BiScrollable;
import com.phonepe.sentinelai.session.QueryDirection;
import com.phonepe.sentinelai.session.SessionStore;
import com.phonepe.sentinelai.session.SessionSummary;
import com.phonepe.sentinelai.storage.ESClient;
import com.phonepe.sentinelai.storage.IndexSettings;
import com.phonepe.sentinelai.storage.memory.ESAgentMemoryDocument;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.ingest.Processor;
import co.elastic.clients.elasticsearch.ingest.SetProcessor;
import co.elastic.clients.json.JsonData;
import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static co.elastic.clients.elasticsearch._types.Refresh.True;

/**
 * Storage for session data
 */
@Slf4j
public class ESSessionStore implements SessionStore {
    private static final String SESSIONS_INDEX = "agent-sessions";

    private static final String SESSION_AUTO_UPDATE_PIPELINE = "update_session_summary_created_updated";

    private static final String MESSAGE_INDEX = "agent-messages";
    private static final String MESSAGE_AUTO_UPDATE_PIPELINE = "update_messages_created_updated";

    private final ESClient client;
    private final String indexPrefix;

    private final ObjectMapper mapper;

    @Builder
    public ESSessionStore(@NonNull ESClient client,
                          String indexPrefix,
                          IndexSettings sessionIndexSettings,
                          IndexSettings messageIndexSettings,
                          ObjectMapper mapper) {
        this.client = client;
        this.indexPrefix = indexPrefix;
        this.mapper = Objects.requireNonNullElseGet(mapper,
                                                    JsonUtils::createMapper);
        ensureSessionIndex(Objects.requireNonNullElse(sessionIndexSettings,
                                                      IndexSettings.DEFAULT));
        ensureMessageIndex(Objects.requireNonNullElse(messageIndexSettings,
                                                      IndexSettings.DEFAULT));
    }

    @Override
    @SneakyThrows
    public boolean deleteSession(String sessionId) {
        final var result = client.getElasticsearchClient()
                .delete(d -> d.index(sessionIndexName())
                        .id(sessionId)
                        .refresh(True));
        log.info("Result of deleting session {}: {}",
                 sessionId,
                 result.result());
        return true;
    }

    @Override
    @SneakyThrows
    public BiScrollable<AgentMessage> readMessages(String sessionId,
                                                   int count,
                                                   boolean skipSystemPrompt,
                                                   BiScrollable.DataPointer inPointer,
                                                   QueryDirection queryDirection) {
        final var olderPointerStr = AgentUtils.getIfNotNull(inPointer,
                                                            BiScrollable.DataPointer::getOlder,
                                                            null);
        final var newerPointerStr = AgentUtils.getIfNotNull(inPointer,
                                                            BiScrollable.DataPointer::getNewer,
                                                            null);
        final var nextPointerStr = queryDirection == QueryDirection.OLDER
                ? olderPointerStr : newerPointerStr;

        final var pointer = Strings.isNullOrEmpty(nextPointerStr) ? null : mapper
                .readValue(nextPointerStr, MessageScrollPointer.class);
        final var queryBuilder = new SearchRequest.Builder().index(
                                                                   messagesIndexName());
        final var boolBuilder = new BoolQuery.Builder();
        boolBuilder.filter(f -> f.term(t -> t.field(
                                                    ESMessageDocument.Fields.sessionId)
                .value(sessionId)));
        if (skipSystemPrompt) {
            boolBuilder.mustNot(f -> f.term(t -> t.field(
                                                         ESMessageDocument.Fields.messageType)
                    .value(AgentMessageType.SYSTEM_PROMPT_REQUEST_MESSAGE
                            .name())));
        }
        queryBuilder.query(q -> q.bool(boolBuilder.build()));

        final var sortOrder = queryDirection == QueryDirection.NEWER
                ? SortOrder.Asc : SortOrder.Desc;

        queryBuilder.sort(s -> s.field(f -> f.field(
                                                    ESMessageDocument.Fields.timestamp)
                .order(sortOrder)))
                .sort(s -> s.field(f -> f.field(
                                                ESMessageDocument.Fields.messageId)
                        .order(sortOrder)));
        if (null != pointer) {
            queryBuilder.searchAfter(List.of(FieldValue.of(pointer.timestamp()),
                                             FieldValue.of(pointer.id())));
        }
        final var searchResult = client.getElasticsearchClient()
                .search(queryBuilder.build(), ESMessageDocument.class);
        final var hits = searchResult.hits().hits();

        if (hits.isEmpty()) {
            return new BiScrollable<>(List.of(),
                                      new BiScrollable.DataPointer(olderPointerStr,
                                                                   newerPointerStr));
        }

        final var documents = hits.stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .limit(count)
                .toList();

        final var firstHit = hits.get(0);
        final var lastHit = hits.get(hits.size() - 1);

        final var hit0Ptr = mapper.writeValueAsString(new MessageScrollPointer(
                                                                               firstHit.sort().get(0).longValue(),
                                                                               firstHit.sort().get(1).stringValue()));

        final var hitLastPtr = mapper.writeValueAsString(new MessageScrollPointer(
                                                                                  lastHit.sort().get(0).longValue(),
                                                                                  lastHit.sort().get(1).stringValue()));

        final var oldestResultPtr = (queryDirection == QueryDirection.NEWER) ? hit0Ptr : hitLastPtr;
        final var newestResultPtr = (queryDirection == QueryDirection.NEWER) ? hitLastPtr : hit0Ptr;

        final var outPointer = switch (queryDirection) {
            case NEWER -> {
                final var latestPtr = newestResultPtr;
                final var oldestPtr = (olderPointerStr == null) ? oldestResultPtr : olderPointerStr;
                yield new BiScrollable.DataPointer(oldestPtr, latestPtr);
            }
            case OLDER -> {
                final var oldestPtr = oldestResultPtr;
                final var latestPtr = (newerPointerStr == null) ? newestResultPtr : newerPointerStr;
                yield new BiScrollable.DataPointer(oldestPtr, latestPtr);
            }
        };

        final var convertedMessages = documents.stream()
                .map(this::toWireMessage)
                .sorted(Comparator.comparingLong(AgentMessage::getTimestamp)
                        .thenComparing(AgentMessage::getMessageId))
                .toList();

        return new BiScrollable<>(List.copyOf(convertedMessages), outPointer);
    }

    @Override
    @SneakyThrows
    public void saveMessages(String sessionId,
                             String runId,
                             List<AgentMessage> messages) {
        final var indexName = messagesIndexName();
        final var bulkOpBuilder = new BulkRequest.Builder();
        final var currIndex = new AtomicInteger(0);
        final var idPrefix = UUID.nameUUIDFromBytes((sessionId + "-" + runId)
                .getBytes(StandardCharsets.UTF_8)).toString();
        messages.forEach(message -> {
            final var storedMessage = toStoredMessage(sessionId,
                                                      runId,
                                                      message);
            bulkOpBuilder.operations(op -> op.update(idx -> idx.index(indexName)
                    .id("%s-%04d".formatted(idPrefix,
                                            currIndex.getAndIncrement()))
                    .action(u -> u.doc(storedMessage).docAsUpsert(true))));
        });
        bulkOpBuilder.refresh(True);
        final var response = client.getElasticsearchClient()
                .bulk(bulkOpBuilder.build());
        log.debug("Bulk message indexing response: {}", response);
    }


    @Override
    @SneakyThrows
    public Optional<SessionSummary> saveSession(SessionSummary sessionSummary) {
        final var stored = toStoredSession(sessionSummary);
        final var indexName = sessionIndexName();
        final var result = client.getElasticsearchClient()
                .update(u -> u.index(indexName)
                        .id(stored.getSessionId())
                        .doc(stored)
                        .docAsUpsert(true)
                        .refresh(True), ESSessionDocument.class)
                .result();

        log.debug("Result of indexing: {}", result);
        return session(sessionSummary.getSessionId());
    }

    @Override
    @SneakyThrows
    public Optional<SessionSummary> session(String sessionId) {
        final var indexName = sessionIndexName();
        final var doc = client.getElasticsearchClient()
                .get(g -> g.index(indexName).id(sessionId).refresh(true),
                     ESSessionDocument.class);
        if (doc.found() && doc.source() != null) {
            return Optional.of(doc.source()).map(this::toWireSession);
        }
        return Optional.empty();
    }

    @Override
    @SneakyThrows
    public BiScrollable<SessionSummary> sessions(int count,
                                                 String pointer,
                                                 QueryDirection queryDirection) {
        final var indexName = sessionIndexName();
        final var searchResult = client.getElasticsearchClient()
                .search(s -> sessionQuery(count,
                                          indexName,
                                          s,
                                          pointer,
                                          queryDirection),
                        ESSessionDocument.class);
        final var hits = searchResult.hits().hits();
        if (hits.isEmpty()) {
            return new BiScrollable<>(List.of(), new BiScrollable.DataPointer(null, null));
        }

        final var firstHit = hits.get(0);
        final var lastHit = hits.get(hits.size() - 1);

        final var hit0Ptr = mapper.writeValueAsString(new SessionScrollPointer(
                                                                               firstHit.sort().get(0).longValue(),
                                                                               firstHit.sort().get(1).stringValue()));

        final var hitLastPtr = mapper.writeValueAsString(new SessionScrollPointer(
                                                                                  lastHit.sort().get(0).longValue(),
                                                                                  lastHit.sort().get(1).stringValue()));

        final var summaries = hits.stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .map(this::toWireSession)
                .toList();

        final var oldestResultPtr = (queryDirection == QueryDirection.NEWER) ? hit0Ptr : hitLastPtr;
        final var newestResultPtr = (queryDirection == QueryDirection.NEWER) ? hitLastPtr : hit0Ptr;

        final var older = queryDirection == QueryDirection.OLDER ? oldestResultPtr : (pointer == null ? oldestResultPtr
                : null);
        final var newer = queryDirection == QueryDirection.NEWER ? newestResultPtr : (pointer == null ? newestResultPtr
                : null);

        return new BiScrollable<>(summaries,
                                  new BiScrollable.DataPointer(older, newer));
    }

    record MessageScrollPointer(
            long timestamp,
            String id
    ) {
    }

    /**
     * Pointer for scroll based pagination
     *
     * @param timestamp A microsecond timestamp used to identify the update time
     * @param id        ID to search for
     */
    record SessionScrollPointer(
            long timestamp,
            String id
    ) {
    }

    private void ensureIndex(IndexSettings indexSettings,
                             String indexName,
                             String pipelineName,
                             UnaryOperator<CreateIndexRequest.Builder> mapper,
                             String createdAtField,
                             String updatedAtField) throws IOException {
        final var elasticsearchClient = client.getElasticsearchClient();
        if (elasticsearchClient.indices()
                .exists(ex -> ex.index(indexName))
                .value()) {
            log.info("Index {} already exists", indexName);
        }
        else {
            log.info("Creating index {}", indexName);
            final var creationStatus = elasticsearchClient.indices()
                    .create(ex -> mapper.apply(ex.index(indexName))
                            .settings(s -> s.numberOfShards(Integer.toString(
                                                                             indexSettings
                                                                                     .getShards()))
                                    .numberOfReplicas(Integer.toString(
                                                                       indexSettings
                                                                               .getReplicas()))
                                    .defaultPipeline(pipelineName)))
                    .acknowledged();
            log.info("Index creation status for index {}: {}",
                     indexName,
                     creationStatus);
            if (elasticsearchClient.ingest()
                    .getPipeline(p -> p.id(pipelineName))
                    .result()
                    .isEmpty()) {
                log.info("Creating pipeline {} for timestamp update in memory document",
                         pipelineName);
                final var pipelineCreated = elasticsearchClient.ingest()
                        .putPipeline(p -> p.id(pipelineName)
                                .description("Update created and updated fields for sessions")
                                .processors(List.of(new Processor(
                                                                  new SetProcessor.Builder()
                                                                          .if_("ctx?.createdAt == null")
                                                                          .field(createdAtField)
                                                                          .override(false)
                                                                          .value(JsonData
                                                                                  .of("{{_ingest.timestamp}}"))
                                                                          .build()),
                                                    new Processor(new SetProcessor.Builder()
                                                            .field(updatedAtField)
                                                            .override(true)
                                                            .value(JsonData.of(
                                                                               "{{_ingest.timestamp}}"))
                                                            .build()))))
                        .acknowledged();
                log.info("Pipeline {} creation status: {}",
                         pipelineName,
                         pipelineCreated);
            }
        }
    }

    @SneakyThrows
    private void ensureMessageIndex(IndexSettings indexSettings) {
        ensureIndex(indexSettings,
                    messagesIndexName(),
                    MESSAGE_AUTO_UPDATE_PIPELINE,
                    builder -> builder.mappings(mapping -> mapping.properties(
                                                                              ESMessageDocument.Fields.sessionId,
                                                                              p -> p.keyword(t -> t))
                            .properties(ESMessageDocument.Fields.runId,
                                        p -> p.keyword(t -> t))
                            .properties(ESMessageDocument.Fields.messageId,
                                        p -> p.keyword(t -> t))
                            .properties(ESMessageDocument.Fields.messageType,
                                        p -> p.keyword(t -> t))
                            .properties(ESMessageDocument.Fields.timestamp,
                                        p -> p.long_(t -> t))
                            .properties(ESMessageDocument.Fields.content,
                                        p -> p.text(t -> t.store(false)
                                                .index(false)))
                            .properties(ESAgentMemoryDocument.Fields.topics,
                                        p -> p.keyword(t -> t))
                            .properties(ESAgentMemoryDocument.Fields.createdAt,
                                        p -> p.date(t -> t))
                            .properties(ESAgentMemoryDocument.Fields.updatedAt,
                                        p -> p.date(t -> t))),
                    ESMessageDocument.Fields.createdAt,
                    ESMessageDocument.Fields.updatedAt);
    }

    @SneakyThrows
    private void ensureSessionIndex(IndexSettings indexSettings) {
        ensureIndex(indexSettings,
                    sessionIndexName(),
                    SESSION_AUTO_UPDATE_PIPELINE,
                    builder -> builder.mappings(mapping -> mapping.properties(
                                                                              ESSessionDocument.Fields.sessionId,
                                                                              p -> p.keyword(t -> t))
                            .properties(ESSessionDocument.Fields.summary,
                                        p -> p.text(t -> t))
                            .properties(ESSessionDocument.Fields.topics,
                                        p -> p.keyword(t -> t))
                            .properties(ESSessionDocument.Fields.raw,
                                        p -> p.text(t -> t.store(false)
                                                .index(false)))
                            .properties(ESSessionDocument.Fields.lastSummarizedMessageId,
                                        p -> p.text(t -> t.store(false)
                                                .index(false)))
                            .properties(ESSessionDocument.Fields.updatedAtMicro,
                                        p -> p.long_(t -> t))
                            .properties(ESSessionDocument.Fields.createdAt,
                                        p -> p.date(t -> t))
                            .properties(ESSessionDocument.Fields.updatedAt,
                                        p -> p.date(t -> t))),
                    ESSessionDocument.Fields.createdAt,
                    ESSessionDocument.Fields.updatedAt);
    }

    private String messagesIndexName() {
        return Strings.isNullOrEmpty(indexPrefix) ? MESSAGE_INDEX : "%s.%s"
                .formatted(indexPrefix, MESSAGE_INDEX);
    }

    private String sessionIndexName() {
        return Strings.isNullOrEmpty(indexPrefix) ? SESSIONS_INDEX : "%s.%s"
                .formatted(indexPrefix, SESSIONS_INDEX);
    }

    private SearchRequest.Builder sessionQuery(final int count,
                                               final String indexName,
                                               final SearchRequest.Builder searchBuilder,
                                               final String nextPagePointer,
                                               final QueryDirection queryDirection) {
        final var sortOrder = queryDirection == QueryDirection.NEWER
                ? SortOrder.Asc : SortOrder.Desc;
        searchBuilder.index(indexName)
                .sort(so -> so.field(f -> f.field(
                                                  ESSessionDocument.Fields.updatedAtMicro)
                        .order(sortOrder)))
                .sort(so -> so.field(f -> f.field(
                                                  ESSessionDocument.Fields.sessionId)
                        .order(sortOrder)))
                .size(count);
        if (!Strings.isNullOrEmpty(nextPagePointer)) {
            searchBuilder.searchAfter(sortOptions(nextPagePointer));
        }
        return searchBuilder;
    }

    @SneakyThrows
    private List<FieldValue> sortOptions(String pointer) {
        if (Strings.isNullOrEmpty(pointer)) {
            return List.of();
        }
        final var scrollPointer = mapper.readValue(pointer,
                                                   SessionScrollPointer.class);
        final List<FieldValue> sortOptions = new ArrayList<>();
        sortOptions.add(FieldValue.of(scrollPointer.timestamp()));
        sortOptions.add(FieldValue.of(scrollPointer.id()));
        return sortOptions;
    }

    @SneakyThrows
    private ESMessageDocument toStoredMessage(String sessionId,
                                              String runId,
                                              AgentMessage message) {
        return ESMessageDocument.builder()
                .sessionId(sessionId)
                .runId(runId)
                .messageId(message.getMessageId())
                .messageType(message.getMessageType())
                .timestamp(message.getTimestamp())
                .content(mapper.writeValueAsString(message))
                .build();
    }

    @SneakyThrows
    private ESSessionDocument toStoredSession(SessionSummary sessionSummary) {
        return ESSessionDocument.builder()
                .sessionId(sessionSummary.getSessionId())
                .summary(sessionSummary.getSummary())
                .topics(sessionSummary.getKeywords())
                .raw(mapper.writeValueAsString(sessionSummary.getRaw()))
                .lastSummarizedMessageId(sessionSummary
                        .getLastSummarizedMessageId())
                .updatedAtMicro(sessionSummary.getUpdatedAt())
                .build();
    }


    @SneakyThrows
    private AgentMessage toWireMessage(ESMessageDocument document) {
        return mapper.readValue(document.getContent(), AgentMessage.class);
    }

    private SessionSummary toWireSession(ESSessionDocument document) {
        return SessionSummary.builder()
                .sessionId(document.getSessionId())
                .summary(document.getSummary())
                .keywords(document.getTopics())
                .raw(document.getRaw())
                .lastSummarizedMessageId(document.getLastSummarizedMessageId())
                .updatedAt(document.getUpdatedAtMicro())
                .build();
    }

}
