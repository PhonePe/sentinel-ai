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

import com.google.common.base.CaseFormat;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Base class for all classes that need an elasticsearch container for integration tests
 */
public class ESIntegrationTestBase {
    protected static final ElasticsearchContainer ELASTICSEARCH_CONTAINER = new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:8.17.3")
            .withEnv(Map.of(
                    "xpack.license.self_generated.type", "basic",
                    "xpack.security.enabled", "false",
                    "discovery.type", "single-node"))
            .withCreateContainerCmdModifier(container -> Objects.requireNonNull(container.getHostConfig())
                    .withMemory(4 * 1024 * 1024 * 1024L))
            .withStartupTimeout(Duration.ofMinutes(5));

    //NOTE::DO NOT USE @Container ETC ANNOTATIONS FOR DOING THIS. READ THE TestContainers DOCUMENTATION.
    static {
        ELASTICSEARCH_CONTAINER.start();
    }

    protected final<T extends ESIntegrationTestBase> String indexPrefix(T test) {
        return CaseFormat.UPPER_CAMEL.converterTo(CaseFormat.LOWER_UNDERSCORE).convert(test.getClass().getSimpleName());
    }
}
