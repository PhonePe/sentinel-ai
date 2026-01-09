package com.phonepe.sentinelai.storage.session;

import co.elastic.clients.elasticsearch._types.Refresh;
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
import com.phonepe.sentinel.session.SessionStore;
import com.phonepe.sentinel.session.SessionSummary;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.utils.AgentUtils;
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
                .id(sessionId), ESSessionDocument.class);
        if (doc.found() && doc.source() != null) {
            return Optional.of(doc.source())
                    .map(this::toWireSession);
        }
        return Optional.empty();
    }

    @Override
    @SneakyThrows
    public List<SessionSummary> sessions(String agentName) {
        final var indexName = sessionIndexName();
        final var searchResult = client.getElasticsearchClient()
                .search(s -> s.index(indexName)
                                .query(q -> q.term(t -> t.field(ESSessionDocument.Fields.agentName)
                                        .value(agentName)))
                                .scroll(sc -> sc.time("1m"))
                        , ESSessionDocument.class);
        final var scrollId = searchResult.scrollId();
        final var hits = searchResult.hits().hits();

        final var documents = new ArrayList<>(hits.stream()
                                                      .map(Hit::source)
                                                      .filter(Objects::nonNull)
                                                      .toList());

        while (!hits.isEmpty()) {
            final var scrollResult = client.getElasticsearchClient()
                    .scroll(s -> s.scrollId(scrollId)
                            .scroll(sc -> sc.time("1m")), ESSessionDocument.class);
            documents.addAll(scrollResult.hits().hits().stream()
                                     .map(Hit::source)
                                     .filter(Objects::nonNull)
                                     .toList());
            if (scrollResult.hits().hits().isEmpty()) {
                break;
            }
        }

        return documents.stream()
                .map(this::toWireSession)
                .toList();
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
                                .refresh(Refresh.True),
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
        messages.forEach(message -> {
            final var storedMessage = toStoredMessage(sessionId, runId, message);
            bulkOpBuilder.operations(op -> op.update(
                    idx -> idx.index(indexName)
                            .id("%s-%04d".formatted(storedMessage.getRunId(), currIndex.getAndIncrement()))
                            .action(u -> u.doc(storedMessage)
                                    .docAsUpsert(true))));
        });
        bulkOpBuilder.refresh(Refresh.True);
        final var response = client.getElasticsearchClient()
                .bulk(bulkOpBuilder.build());
        log.debug("Bulk message indexing response: {}", response);
    }

    @Override
    @SneakyThrows
    public List<AgentMessage> readMessages(String sessionId, int count, boolean skipSystemPrompt) {
        final var indexName = messagesIndexName();
        final var queryBuilder = new SearchRequest.Builder().index(messagesIndexName());
        final var boolBuilder = new BoolQuery.Builder();
        boolBuilder.filter(f -> f.term(t ->
                                               t.field(ESMessageDocument.Fields.sessionId).value(sessionId)));
        if(skipSystemPrompt) {
            boolBuilder.mustNot(f -> f.term(t -> t.field(ESMessageDocument.Fields.messageType)
                    .value(AgentMessageType.SYSTEM_PROMPT_REQUEST_MESSAGE.name())));
        }
        queryBuilder.query(q -> q.bool(boolBuilder.build()));
        final var searchResult = client.getElasticsearchClient()
                /*.search(s -> s.index(indexName)
                                .query(q -> q.term(t -> t.field(ESMessageDocument.Fields.sessionId)
                                        .value(sessionId)))
//                                .sort(sort -> sort
//                                        .field(f -> f.field(ESMessageDocument.Fields.timestamp).order(SortOrder.Desc)))
                                .scroll(sc -> sc.time("1m"))
                        , ESMessageDocument.class);*/
                .search(queryBuilder.scroll(sc -> sc.time("1m")).build(), ESMessageDocument.class);
        final var scrollId = searchResult.scrollId();
        final var hits = searchResult.hits().hits();

        final var documents = new ArrayList<>(hits.stream()
                                                      .map(Hit::source)
                                                      .filter(Objects::nonNull)
                                                      .limit(count)
                                                      .toList());

        while (!hits.isEmpty()) {
            final var scrollResult = client.getElasticsearchClient()
                    .scroll(s -> s.scrollId(scrollId)
                            .scroll(sc -> sc.time("1m")), ESMessageDocument.class);
            documents.addAll(scrollResult.hits().hits().stream()
                                     .map(Hit::source)
                                     .filter(Objects::nonNull)
                                     .toList());
            if (scrollResult.hits().hits().isEmpty() || documents.size() >= count) {
                break;
            }
        }

        return documents.stream()
                .limit(count)
                .map(this::toWireMessage)
                .sorted(Comparator.comparingLong(AgentMessage::getTimestamp))
                .toList();
    }


    private static String docIdForMessage(ESMessageDocument storedMessage) {
        return UUID.nameUUIDFromBytes(AgentUtils.id(storedMessage.getSessionId(),
                                                    storedMessage.getMessageId())
                                              .getBytes(StandardCharsets.UTF_8)).toString();
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
