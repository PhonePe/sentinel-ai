package com.phonepe.sentinelai.storage.memory;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.ingest.Processor;
import co.elastic.clients.elasticsearch.ingest.SetProcessor;
import co.elastic.clients.json.JsonData;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.agentmemory.AgentMemory;
import com.phonepe.sentinelai.agentmemory.AgentMemoryStore;
import com.phonepe.sentinelai.agentmemory.MemoryScope;
import com.phonepe.sentinelai.agentmemory.MemoryType;
import com.phonepe.sentinelai.embedding.EmbeddingModel;
import com.phonepe.sentinelai.storage.ESClient;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * An implementation of memory store that uses elasticsearch as the backend
 */
@Slf4j
public class ESAgentMemoryStorage implements AgentMemoryStore {
    private static final String MEMORIES_INDEX = "agent-memories";
    private static final String AUTO_UPDATE_PIPELINE = "update_agent_memories_created_updated";

    private final ESClient client;
    private final EmbeddingModel embeddingModel;
    private final String indexPrefix;

    public ESAgentMemoryStorage(@NonNull ESClient client, @NonNull EmbeddingModel embeddingModel, String indexPrefix) {
        this.client = client;
        this.embeddingModel = embeddingModel;
        this.indexPrefix = indexPrefix;
        ensureIndex();
    }

    @Override
    @SneakyThrows
    public List<AgentMemory> findMemories(
            String scopeId,
            MemoryScope scope,
            Set<MemoryType> memoryTypes,
            List<String> topics, String query,
            int minReusabilityScore,
            int count) {
        final var queryBuilder = new SearchRequest.Builder().index(indexName());
        final var boolBuilder = new BoolQuery.Builder();
        //Filter on scope
        //If looking for agent scoped memory only scope is okay
        if(scope != null && !Strings.isNullOrEmpty(scopeId)) {
            switch (scope) {
                case AGENT -> boolBuilder.filter(f -> f.term(m ->
                                                                     m.field(ESAgentMemoryDocument.Fields.scope)
                                                                             .value(scope.name())));
                case ENTITY -> boolBuilder.filter(f -> f.term(m ->
                                                                      m.field(ESAgentMemoryDocument.Fields.scopeId)
                                                                              .value(scopeId)))
                        .filter(f -> f.term(m ->
                                                    m.field(ESAgentMemoryDocument.Fields.scope).value(scope.name())));
            }
        }
        if (null != memoryTypes && !memoryTypes.isEmpty()) {
            boolBuilder.filter(f -> f.terms(m -> m.field(ESAgentMemoryDocument.Fields.memoryType)
                    .terms(new TermsQueryField.Builder()
                                   .value(memoryTypes.stream()
                                                  .map(mt -> FieldValue.of(mt.name()))
                                                  .toList())
                                   .build())));
        }
        if (null != topics && !topics.isEmpty()) {
            boolBuilder.filter(f -> f.terms(m -> m.field(ESAgentMemoryDocument.Fields.topics)
                    .terms(new TermsQueryField.Builder()
                                   .value(topics.stream()
                                                .map(FieldValue::of)
                                                .toList())
                                   .build())));
        }
        if(minReusabilityScore > 0) {
            boolBuilder.filter(f -> f.range(r -> r.number(m -> m.field(ESAgentMemoryDocument.Fields.reusabilityScore)
                    .gte((double)minReusabilityScore))));
        }
        //If a text query is sent, use text query along with cosine based vector search to get relevant results
        if (!Strings.isNullOrEmpty(query)) {
//            boolBuilder.must(m -> m.match(q -> q.field(ESAgentMemoryDocument.Fields.content).query(query)));
            final var embedding = embeddingModel.getEmbedding(query);
            final var embeddingList = new ArrayList<Float>(embedding.length);
            for (float v : embedding) {
                embeddingList.add(v);
            }
            boolBuilder.must(m -> m.knn(k -> k.field(ESAgentMemoryDocument.Fields.contentVector)
                    .queryVector(embeddingList)
                    .k(count)));
        }
        queryBuilder.query(q -> q.bool(boolBuilder.build()));
        return client.getElasticsearchClient()
                .search(queryBuilder.size(count).build(), ESAgentMemoryDocument.class)
                .hits()
                .hits()
                .stream()
                .filter(hit -> null != hit.source())
                .map(hit -> toWire(hit.source()))
                .toList();

    }

    @Override
    @SneakyThrows
    public Optional<AgentMemory> save(AgentMemory agentMemory) {
        final var stored = toStored(agentMemory);
        final var indexName = indexName();
        final var result = client.getElasticsearchClient()
                .update(u -> u.index(indexName)
                                .id(stored.getId())
                                .doc(stored)
                                .docAsUpsert(true)
                                .refresh(Refresh.True),
                        ESAgentMemoryDocument.class
                       )
                .result();

        log.info("Result of indexing: {}", result);
        final var doc = client.getElasticsearchClient().get(g -> g.index(indexName)
                .id(stored.getId()), ESAgentMemoryDocument.class);
        if (doc.found() && doc.source() != null) {
            return Optional.of(doc.source())
                    .map(this::toWire);
        }
        return Optional.empty();
    }

    @SneakyThrows
    private void ensureIndex() {
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
                                              .properties(ESAgentMemoryDocument.Fields.agentName,
                                                          p -> p.keyword(t -> t))
                                              .properties(ESAgentMemoryDocument.Fields.scope, p -> p.keyword(t -> t))
                                              .properties(ESAgentMemoryDocument.Fields.scopeId, p -> p.keyword(t -> t))
                                              .properties(ESAgentMemoryDocument.Fields.memoryType,
                                                          p -> p.keyword(t -> t))
                                              .properties(ESAgentMemoryDocument.Fields.name, p -> p.text(t -> t))
                                              .properties(ESAgentMemoryDocument.Fields.content, p -> p.text(t -> t))
                                              .properties(ESAgentMemoryDocument.Fields.contentVector,
                                                          p -> p.denseVector(t -> t.dims(384)
                                                                  .elementType("float")
                                                                  .similarity("cosine")
                                                                  .index(true)
                                                                  .indexOptions(i -> i.type("hnsw"))))
                                              .properties(ESAgentMemoryDocument.Fields.topics, p -> p.keyword(t -> t))
                                              .properties(ESAgentMemoryDocument.Fields.reusabilityScore,
                                                          p -> p.integer(t -> t))
                                              .properties(ESAgentMemoryDocument.Fields.createdAt, p -> p.date(t -> t))
                                              .properties(ESAgentMemoryDocument.Fields.updatedAt, p -> p.date(t -> t))
                                     )
                            .settings(s -> s.numberOfShards("1")
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
                                .description("Update created and updated fields for agent memory")
                                .processors(List.of(new Processor(new SetProcessor.Builder()
                                                                          .if_("ctx?.createdAt == null")
                                                                          .field(ESAgentMemoryDocument.Fields.createdAt)
                                                                          .override(false)
                                                                          .value(JsonData.of("{{_ingest.timestamp}}"))
                                                                          .build()),
                                                    new Processor(new SetProcessor.Builder()
                                                                          .field(ESAgentMemoryDocument.Fields.updatedAt)
                                                                          .override(true)
                                                                          .value(JsonData.of("{{_ingest.timestamp}}"))
                                                                          .build()))))
                        .acknowledged();
                log.info("Pipeline {} creation status: {}", AUTO_UPDATE_PIPELINE, pipelineCreated);
            }
        }
    }

    private AgentMemory toWire(final ESAgentMemoryDocument agentMemoryDocument) {
        return AgentMemory.builder()
                .agentName(agentMemoryDocument.getAgentName())
                .scope(agentMemoryDocument.getScope())
                .scopeId(agentMemoryDocument.getScopeId())
                .memoryType(agentMemoryDocument.getMemoryType())
                .name(agentMemoryDocument.getName())
                .content(agentMemoryDocument.getContent())
                .topics(agentMemoryDocument.getTopics())
                .reusabilityScore(agentMemoryDocument.getReusabilityScore())
                .createdAt(agentMemoryDocument.getCreatedAt())
                .updatedAt(agentMemoryDocument.getUpdatedAt())
                .build();
    }

    private ESAgentMemoryDocument toStored(final AgentMemory agentMemory) {
        return ESAgentMemoryDocument.builder()
                .id(UUID.nameUUIDFromBytes("%s-%s-%s-%s".formatted(agentMemory.getAgentName(),
                                                                   agentMemory.getScope(),
                                                                   agentMemory.getScopeId(),
                                                                   agentMemory.getName())
                                                   .getBytes(StandardCharsets.UTF_8)).toString())
                .agentName(agentMemory.getAgentName())
                .scope(agentMemory.getScope())
                .scopeId(agentMemory.getScopeId())
                .memoryType(agentMemory.getMemoryType())
                .name(agentMemory.getName())
                .content(agentMemory.getContent())
                .contentVector(embeddingModel.getEmbedding(agentMemory.getContent()))
                .topics(agentMemory.getTopics())
                .reusabilityScore(agentMemory.getReusabilityScore())
                .build();
    }

    private String indexName() {
        return Strings.isNullOrEmpty(indexPrefix) ? MEMORIES_INDEX : "%s.%s".formatted(indexPrefix, MEMORIES_INDEX);
    }
}
