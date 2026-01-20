package com.phonepe.sentinelai.storage.session;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.phonepe.sentinelai.session.SessionStore;
import com.phonepe.sentinelai.session.SessionSummary;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.storage.ESClient;
import com.phonepe.sentinelai.storage.IndexSettings;
import com.phonepe.sentinelai.storage.memory.ESAgentMemoryDocument;
import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static co.elastic.clients.elasticsearch._types.Refresh.True;

/**
 * Storage for session data
 */
@Slf4j
public class ESSessionStore implements SessionStore {
    /**
     * Pointer for scroll based pagination
     * @param timestamp A microsecond timestamp used to identify the update time
     * @param id ID to search for
     */
    record SessionScrollPointer(long timestamp, String id) {}
    record MessageScrollPointer(long timestamp, String id) {}

    private static final String SESSIONS_INDEX = "agent-sessions";
    private static final String SESSION_AUTO_UPDATE_PIPELINE = "update_session_summary_created_updated";

    private static final String MESSAGE_INDEX = "agent-messages";
    private static final String MESSAGE_AUTO_UPDATE_PIPELINE = "update_messages_created_updated";

    private final ESClient client;
    private final String indexPrefix;
    private final ObjectMapper mapper;

    @Builder
    public ESSessionStore(
            @NonNull ESClient client,
            String indexPrefix,
            IndexSettings sessionIndexSettings,
            IndexSettings messageIndexSettings,
            ObjectMapper mapper) {
        this.client = client;
        this.indexPrefix = indexPrefix;
        this.mapper = Objects.requireNonNullElseGet(mapper, JsonUtils::createMapper);
        ensureSessionIndex(Objects.requireNonNullElse(sessionIndexSettings, IndexSettings.DEFAULT));
        ensureMessageIndex(Objects.requireNonNullElse(messageIndexSettings, IndexSettings.DEFAULT));
    }

    @Override
    @SneakyThrows
    public Optional<SessionSummary> session(String sessionId) {
        final var indexName = sessionIndexName();
        final var doc = client.getElasticsearchClient().get(g -> g.index(indexName)
                .id(sessionId)
                .refresh(true), ESSessionDocument.class);
        if (doc.found() && doc.source() != null) {
            return Optional.of(doc.source())
                    .map(this::toWireSession);
        }
        return Optional.empty();
    }


    @Override
    @SneakyThrows
    public ListResponse<SessionSummary> sessions(
            int count,
            String nextPagePointer) {
        final var indexName = sessionIndexName();
        final var searchResult = client.getElasticsearchClient()
                .search(s -> sessionQuery(count, indexName, s, nextPagePointer),
                        ESSessionDocument.class);
        final var hits = searchResult.hits().hits();
        final var summaries = hits.stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .map(this::toWireSession)
                .toList();
        var scrollId =  "";
        if(!summaries.isEmpty()) {
            final var sort = hits.get(hits.size() - 1).sort();
            scrollId = mapper.writeValueAsString(new SessionScrollPointer(
                    sort.get(0).longValue(),
                    sort.get(1).stringValue()
            ));
        }
        return new ListResponse<>(summaries, scrollId);
    }

    @Override
    @SneakyThrows
    public boolean deleteSession(String sessionId) {
        final var result = client.getElasticsearchClient()
                .delete(d -> d.index(sessionIndexName())
                        .id(sessionId)
                        .refresh(True));
        log.info("Result of deleting session {}: {}", sessionId, result.result());
        return true;
    }

    @SneakyThrows
    private List<FieldValue> sortOptions(String pointer) {
        if(Strings.isNullOrEmpty(pointer)) {
            return List.of();
        }
        final var scrollPointer = mapper.readValue(pointer, SessionScrollPointer.class);
        final List<FieldValue> sortOptions = new ArrayList<>();
        sortOptions.add(FieldValue.of(scrollPointer.timestamp()));
        sortOptions.add(FieldValue.of(scrollPointer.id()));
        return sortOptions;
    }

    private SearchRequest.Builder sessionQuery(
            final int count,
            final String indexName,
            final SearchRequest.Builder searchBuilder,
            final String nextPagePointer) {
        searchBuilder.index(indexName)
                .sort(so -> so.field(f -> f.field(ESSessionDocument.Fields.updatedAtMicro)
                        .order(SortOrder.Desc)))
                .sort(so -> so.field(f -> f.field(ESSessionDocument.Fields.sessionId)
                                .order(SortOrder.Desc)))
                .size(count);
        if (!Strings.isNullOrEmpty(nextPagePointer)) {
            searchBuilder.searchAfter(sortOptions(nextPagePointer));
        }
        return searchBuilder;
    }

    @Override
    @SneakyThrows
    public Optional<SessionSummary> saveSession(String agentName, SessionSummary sessionSummary) {
        final var stored = toStoredSession(agentName, sessionSummary);
        final var indexName = sessionIndexName();
        final var result = client.getElasticsearchClient()
                .update(u -> u.index(indexName)
                                .id(stored.getSessionId())
                                .doc(stored)
                                .docAsUpsert(true)
                                .refresh(True),
                        ESSessionDocument.class
                       )
                .result();

        log.debug("Result of indexing: {}", result);
        return session(sessionSummary.getSessionId());
    }

    @Override
    @SneakyThrows
    public void saveMessages(String sessionId, String runId, List<AgentMessage> messages) {
        final var indexName = messagesIndexName();
        final var bulkOpBuilder = new BulkRequest.Builder();
        final var currIndex = new AtomicInteger(0);
        final var idPrefix = UUID.nameUUIDFromBytes((sessionId + "-" + runId).getBytes(StandardCharsets.UTF_8))
                .toString();
        messages.forEach(message -> {
            final var storedMessage = toStoredMessage(sessionId, runId, message);
            bulkOpBuilder.operations(op -> op.update(
                    idx -> idx.index(indexName)
                            .id("%s-%04d".formatted(idPrefix, currIndex.getAndIncrement()))
                            .action(u -> u.doc(storedMessage)
                                    .docAsUpsert(true))));
        });
        bulkOpBuilder.refresh(True);
        final var response = client.getElasticsearchClient()
                .bulk(bulkOpBuilder.build());
        log.debug("Bulk message indexing response: {}", response);
    }

    @Override
    @SneakyThrows
    public ListResponse<AgentMessage> readMessages(
            String sessionId,
            int count,
            boolean skipSystemPrompt,
            String nextPointer) {
        final var pointer = Strings.isNullOrEmpty(nextPointer)
                            ? null
                            : mapper.readValue(nextPointer, MessageScrollPointer.class);
        final var queryBuilder = new SearchRequest.Builder()
                .index(messagesIndexName());
        final var boolBuilder = new BoolQuery.Builder();
        boolBuilder.filter(f -> f.term(t ->
                                               t.field(ESMessageDocument.Fields.sessionId).value(sessionId)));
        if (skipSystemPrompt) {
            boolBuilder.mustNot(f -> f.term(t -> t.field(ESMessageDocument.Fields.messageType)
                    .value(AgentMessageType.SYSTEM_PROMPT_REQUEST_MESSAGE.name())));
        }
        queryBuilder.query(q -> q.bool(boolBuilder.build()));
        queryBuilder.sort(s -> s.field(f -> f.field(ESMessageDocument.Fields.timestamp).order(SortOrder.Desc)))
                .sort(s -> s.field(f -> f.field(ESMessageDocument.Fields.messageId).order(SortOrder.Desc)));
        if (null != pointer) {
            queryBuilder.searchAfter(List.of(FieldValue.of(pointer.timestamp()), FieldValue.of(pointer.id())));
        }
        final var searchResult = client.getElasticsearchClient()
                .search(queryBuilder.build(), ESMessageDocument.class);
        final var hits = searchResult.hits().hits();

        final var documents = new ArrayList<>(hits.stream()
                                                      .map(Hit::source)
                                                      .filter(Objects::nonNull)
                                                      .limit(count)
                                                      .toList());
        final var nextResultSPointer = hits.isEmpty()
                                       ? nextPointer
                                       : mapper.writeValueAsString(new MessageScrollPointer(
                                               hits.get(hits.size() - 1).sort().get(0).longValue(),
                                               hits.get(hits.size() - 1).sort().get(1).stringValue()
                                       ));
        final var convertedMessages = documents.stream()
                .limit(count)
                .map(this::toWireMessage)
                .sorted(Comparator.comparingLong(AgentMessage::getTimestamp))
                .toList();
        return new ListResponse<>(List.copyOf(Lists.reverse(convertedMessages)), nextResultSPointer);
    }

    private SessionSummary toWireSession(ESSessionDocument document) {
        return SessionSummary.builder()
                .sessionId(document.getSessionId())
                .summary(document.getSummary())
                .keywords(document.getTopics())
                .updatedAt(document.getUpdatedAtMicro())
                .build();
    }

    private ESSessionDocument toStoredSession(String agentName, SessionSummary sessionSummary) {
        return ESSessionDocument.builder()
                .sessionId(sessionSummary.getSessionId())
                .agentName(agentName)
                .summary(sessionSummary.getSummary())
                .topics(sessionSummary.getKeywords())
                .updatedAtMicro(sessionSummary.getUpdatedAt())
                .build();
    }

    @SneakyThrows
    private AgentMessage toWireMessage(ESMessageDocument document) {
        return mapper.readValue(document.getContent(), AgentMessage.class);
    }

    @SneakyThrows
    private ESMessageDocument toStoredMessage(String sessionId, String runId, AgentMessage message) {
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
    private void ensureSessionIndex(IndexSettings indexSettings) {
        ensureIndex(indexSettings, sessionIndexName(), SESSION_AUTO_UPDATE_PIPELINE,
                    builder -> builder.mappings(
                            mapping -> mapping
                                    .properties(ESSessionDocument.Fields.sessionId,
                                                p -> p.keyword(t -> t))
                                    .properties(ESSessionDocument.Fields.agentName,
                                                p -> p.keyword(t -> t))
                                    .properties(ESSessionDocument.Fields.summary,
                                                p -> p.text(t -> t))
                                    .properties(ESSessionDocument.Fields.topics,
                                                p -> p.keyword(t -> t))
                                    .properties(ESSessionDocument.Fields.updatedAtMicro,
                                                p -> p.long_(t -> t))
                                    .properties(ESSessionDocument.Fields.createdAt,
                                                p -> p.date(t -> t))
                                    .properties(ESSessionDocument.Fields.updatedAt,
                                                p -> p.date(t -> t))
                                               ),
                    ESSessionDocument.Fields.createdAt,
                    ESSessionDocument.Fields.updatedAt);
    }

    @SneakyThrows
    private void ensureMessageIndex(IndexSettings indexSettings) {
        ensureIndex(indexSettings,
                    messagesIndexName(),
                    MESSAGE_AUTO_UPDATE_PIPELINE,
                    builder -> builder.mappings(
                            mapping -> mapping
                                    .properties(ESMessageDocument.Fields.sessionId,
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
                                                p -> p.text(t -> t.store(false).index(false)))
                                    .properties(ESAgentMemoryDocument.Fields.topics,
                                                p -> p.keyword(t -> t))
                                    .properties(ESAgentMemoryDocument.Fields.createdAt,
                                                p -> p.date(t -> t))
                                    .properties(ESAgentMemoryDocument.Fields.updatedAt,
                                                p -> p.date(t -> t))),
                    ESMessageDocument.Fields.createdAt,
                    ESMessageDocument.Fields.updatedAt);
    }

    private void ensureIndex(
            IndexSettings indexSettings, String indexName,
            String pipelineName,
            UnaryOperator<CreateIndexRequest.Builder> mapper,
            String createdAtField,
            String updatedAtField) throws IOException {
        final var elasticsearchClient = client.getElasticsearchClient();
        if (elasticsearchClient.indices().exists(ex -> ex.index(indexName)).value()) {
            log.info("Index {} already exists", indexName);
        }
        else {
            log.info("Creating index {}", indexName);
            final var creationStatus = elasticsearchClient.indices()
                    .create(ex -> mapper.apply(ex.index(indexName))
                            .settings(s -> s.numberOfShards(Integer.toString(indexSettings.getShards()))
                                    .numberOfReplicas(Integer.toString(indexSettings.getReplicas()))
                                    .defaultPipeline(pipelineName)))
                    .acknowledged();
            log.info("Index creation status for index {}: {}", indexName, creationStatus);
            if (elasticsearchClient
                    .ingest()
                    .getPipeline(p -> p.id(pipelineName))
                    .result()
                    .isEmpty()) {
                log.info("Creating pipeline {} for timestamp update in memory document", pipelineName);
                final var pipelineCreated = elasticsearchClient
                        .ingest()
                        .putPipeline(p -> p.id(pipelineName)
                                .description("Update created and updated fields for sessions")
                                .processors(List.of(new Processor(new SetProcessor.Builder()
                                                                          .if_("ctx?.createdAt == null")
                                                                          .field(createdAtField)
                                                                          .override(false)
                                                                          .value(JsonData.of("{{_ingest.timestamp}}"))
                                                                          .build()),
                                                    new Processor(new SetProcessor.Builder()
                                                                          .field(updatedAtField)
                                                                          .override(true)
                                                                          .value(JsonData.of("{{_ingest.timestamp}}"))
                                                                          .build()))))
                        .acknowledged();
                log.info("Pipeline {} creation status: {}", pipelineName, pipelineCreated);
            }
        }
    }


    private String sessionIndexName() {
        return Strings.isNullOrEmpty(indexPrefix) ? SESSIONS_INDEX : "%s.%s".formatted(indexPrefix, SESSIONS_INDEX);
    }

    private String messagesIndexName() {
        return Strings.isNullOrEmpty(indexPrefix) ? MESSAGE_INDEX : "%s.%s".formatted(indexPrefix, MESSAGE_INDEX);
    }

}
