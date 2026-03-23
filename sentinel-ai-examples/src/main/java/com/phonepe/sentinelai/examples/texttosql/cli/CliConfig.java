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

package com.phonepe.sentinelai.examples.texttosql.cli;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Top-level YAML configuration for the Text-to-SQL CLI.
 *
 * <p>Default location: {@code .env/agent-config.yml} relative to the working directory. Override
 * with {@code --config <path>}.
 *
 * <p>Example file: {@code src/main/resources/.env/agent-config.yml.example}
 */
@Data
@NoArgsConstructor
public class CliConfig {

    /** Agent behaviour settings. */
    @Data
    @NoArgsConstructor
    public static class AgentConfig {
        /** Model temperature. Use 0.0 for deterministic SQL generation. */
        @JsonProperty("temperature")
        private float temperature = 0.0f;

        /** Maximum tokens per model response. */
        @JsonProperty("maxTokens")
        private int maxTokens = 4096;

        /** Whether to stream the assistant response token-by-token. */
        @JsonProperty("streaming")
        private boolean streaming = true;
    }

    /** SQLite database configuration. */
    @Data
    @NoArgsConstructor
    public static class DatabaseConfig {
        /**
         * Path to the SQLite {@code .db} file. The CLI creates and seeds the database from the
         * bundled schema + CSV files if it does not exist.
         */
        @JsonProperty("path")
        private String path = "./ecommerce.db";
    }

    /** OpenAI API credentials and model selection. */
    @Data
    @NoArgsConstructor
    public static class OpenAIConfig {
        /** OpenAI API key (required). */
        @JsonProperty("baseUrl")
        private String baseUrl;

        /** OpenAI API key (required). */
        @JsonProperty("apiKey")
        private String apiKey;

        /** Chat model to use. Defaults to {@code gpt-4o}. */
        @JsonProperty("model")
        private String model = "gpt-4o";
    }

    // -------------------------------------------------------------------------

    @JsonProperty("openai")
    private OpenAIConfig openai = new OpenAIConfig();

    @JsonProperty("database")
    private DatabaseConfig database = new DatabaseConfig();

    @JsonProperty("agent")
    private AgentConfig agent = new AgentConfig();
}
