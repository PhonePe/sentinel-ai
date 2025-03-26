package com.phonepe.sentinelai.storage;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;

/**
 * Elasticsearch client wrapper
 */
public class ESClient implements AutoCloseable {
    @Getter
    private final ElasticsearchClient elasticsearchClient;


    @Builder
    public ESClient(@NonNull String serverUrl, String apiKey) {
        RestClient restClient = RestClient
                .builder(HttpHost.create(serverUrl))
                .setDefaultHeaders(new Header[]{
                        new BasicHeader("Authorization", "ApiKey " + apiKey)
                })
                .build();
        ElasticsearchTransport transport = new RestClientTransport(
                restClient, new JacksonJsonpMapper(JsonUtils.createMapper())
                );
        this.elasticsearchClient = new ElasticsearchClient(transport);

    }

    @Override
    public void close() throws Exception {
        elasticsearchClient.close();
    }
}
