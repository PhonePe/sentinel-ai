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

package com.phonepe.sentinelai.storage;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;

import com.phonepe.sentinelai.core.utils.JsonUtils;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/**
 * Elasticsearch client wrapper
 */
public class ESClient implements AutoCloseable {
    @Getter
    private final ElasticsearchClient elasticsearchClient;


    @Builder
    public ESClient(@NonNull String serverUrl, String apiKey) {
        RestClient restClient = RestClient.builder(HttpHost.create(serverUrl))
                .setDefaultHeaders(new Header[] {new BasicHeader("Authorization", "ApiKey " + apiKey)})
                .build();
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper(JsonUtils
                .createMapper()));
        this.elasticsearchClient = new ElasticsearchClient(transport);

    }

    @Override
    public void close() throws Exception {
        elasticsearchClient.close();
    }
}
