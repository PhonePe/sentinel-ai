package com.phonepe.sentinelai.storage.session;

import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.ingest.Processor;
import co.elastic.clients.elasticsearch.ingest.SetProcessor;
import co.elastic.clients.json.JsonData;
import com.google.common.base.Strings;
import com.phonepe.sentinel.session.SessionStore;
import com.phonepe.sentinel.session.SessionSummary;
import com.phonepe.sentinelai.storage.ESClient;
import com.phonepe.sentinelai.storage.IndexSettings;
import com.phonepe.sentinelai.storage.memory.ESAgentMemoryDocument;
import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Storage for session data
 */
@Slf4j
public class ESSessionStore implements SessionStore {
    private static final String SESSIONS_INDEX = "agent-sessions";
    private static final String AUTO_UPDATE_PIPELINE = "update_session_summary_created_updated";

    private final ESClient client;
    private final String indexPrefix;

    @Builder
    public ESSessionStore(@NonNull ESClient client, String indexPrefix, IndexSettings indexSettings) {
        this.client = client;
        this.indexPrefix = indexPrefix;
        ensureIndex(Objects.requireNonNullElse(indexSettings, IndexSettings.DEFAULT));
    }

    @Override
    @SneakyThrows
    public Optional<SessionSummary> session(String sessionId) {
        final var indexName = indexName();
        final var doc = client.getElasticsearchClient().get(g -> g.index(indexName)
                .id(sessionId), ESSessionDocument.class);
        if (doc.found() && doc.source() != null) {
            return Optional.of(doc.source())
                    .map(this::toWire);
        }
        return Optional.empty();
    }

    @Override
    @SneakyThrows
    public List<SessionSummary> sessions(String agentName) {
        final var indexName = indexName();
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
                .map(this::toWire)
                .toList();
    }

    @Override
    @SneakyThrows
    public Optional<SessionSummary> saveSession(String agentName, SessionSummary sessionSummary) {
        final var stored = toStored(agentName, sessionSummary);
        final var indexName = indexName();
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

    private SessionSummary toWire(ESSessionDocument document) {
        return SessionSummary.builder()
                .sessionId(document.getSessionId())
                .summary(document.getSummary())
                .topics(document.getTopics())
                .build();
    }

    private ESSessionDocument toStored(String agentName, SessionSummary sessionSummary) {
        return ESSessionDocument.builder()
                .sessionId(sessionSummary.getSessionId())
                .agentName(agentName)
                .summary(sessionSummary.getSummary())
                .topics(sessionSummary.getTopics())
                .build();
    }

    @SneakyThrows
    private void ensureIndex(IndexSettings indexSettings) {
        final var elasticsearchClient = client.getElasticsearchClient();
        final var indexName = indexName();
        if (elasticsearchClient.indices().exists(ex -> ex.index(indexName)).value()) {
            log.info("Index {} already exists", indexName);
        }
        else {
            log.info("Creating index {}", indexName);
            final var creationstatus = elasticsearchClient.indices()
                    .create(ex -> ex.index(indexName)
                            .mappings(mapping -> mapping
                                              .properties(ESSessionDocument.Fields.sessionId,
                                                          p -> p.keyword(t -> t))
                                              .properties(ESSessionDocument.Fields.agentName,
                                                          p -> p.keyword(t -> t))
                                              .properties(ESSessionDocument.Fields.summary, p -> p.text(t -> t))
                                              .properties(ESAgentMemoryDocument.Fields.topics, p -> p.keyword(t -> t))
                                              .properties(ESAgentMemoryDocument.Fields.createdAt, p -> p.date(t -> t))
                                              .properties(ESAgentMemoryDocument.Fields.updatedAt, p -> p.date(t -> t))
                                     )
                            .settings(s -> s.numberOfShards(Integer.toString(indexSettings.getShards()))
                                    .numberOfReplicas(Integer.toString(indexSettings.getReplicas()))
                                    .defaultPipeline(AUTO_UPDATE_PIPELINE)))
                    .acknowledged();
            log.info("Index creation status for index {}: {}", indexName, creationstatus);
            if (elasticsearchClient
                    .ingest()
                    .getPipeline(p -> p.id(AUTO_UPDATE_PIPELINE))
                    .result()
                    .isEmpty()) {
                log.info("Creating pipeline {} for timestamp update in memory document", AUTO_UPDATE_PIPELINE);
                final var pipelineCreated = elasticsearchClient
                        .ingest()
                        .putPipeline(p -> p.id(AUTO_UPDATE_PIPELINE)
                                .description("Update created and updated fields for sessions")
                                .processors(List.of(new Processor(new SetProcessor.Builder()
                                                                          .if_("ctx?.createdAt == null")
                                                                          .field(ESSessionDocument.Fields.createdAt)
                                                                          .override(false)
                                                                          .value(JsonData.of("{{_ingest.timestamp}}"))
                                                                          .build()),
                                                    new Processor(new SetProcessor.Builder()
                                                                          .field(ESSessionDocument.Fields.updatedAt)
                                                                          .override(true)
                                                                          .value(JsonData.of("{{_ingest.timestamp}}"))
                                                                          .build()))))
                        .acknowledged();
                log.info("Pipeline {} creation status: {}", AUTO_UPDATE_PIPELINE, pipelineCreated);
            }
        }
    }

    private String indexName() {
        return Strings.isNullOrEmpty(indexPrefix) ? SESSIONS_INDEX : "%s.%s".formatted(indexPrefix, SESSIONS_INDEX);
    }

}
