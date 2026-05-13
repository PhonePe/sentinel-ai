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

import com.fasterxml.jackson.databind.ObjectMapper;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.outputvalidation.OutputValidationResults;
import com.phonepe.sentinelai.examples.texttosql.agent.TextToSqlAgent;
import com.phonepe.sentinelai.examples.texttosql.cli.support.StubTextToSqlModel;
import com.phonepe.sentinelai.examples.texttosql.tools.model.SqlQueryResult;
import com.phonepe.sentinelai.instrumentation.otel.OpenTelemetryAgentExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("TextToSqlCLI OpenTelemetry")
@Disabled
class TextToSqlCliOpenTelemetryTest {
    private InMemorySpanExporter spanExporter;
    private SdkTracerProvider tracerProvider;
    private OpenTelemetrySdk openTelemetrySdk;

    @Test
    @DisplayName("CLI OpenTelemetry extension emits spans for agent execution")
    void cliOpenTelemetryExtensionEmitsSpansForAgentExecution() {
        final ObjectMapper mapper = new ObjectMapper();
        final AgentSetup setup = AgentSetup.builder()
                .mapper(mapper)
                .model(new StubTextToSqlModel())
                .build();
        final OpenTelemetryAgentExtension<String, SqlQueryResult, TextToSqlAgent> otelExtension = TextToSqlCLI
                .buildOpenTelemetryExtension();

        final TextToSqlAgent agent = new TextToSqlAgent(
                                                        setup,
                                                        List.of(otelExtension),
                                                        (context, output) -> OutputValidationResults.success());

        final var output = agent.execute(new AgentInput<>("show me one row", null, null, null, null));

        assertNotNull(output.getData());
        assertEquals("SELECT 1", output.getData().generatedSql());
        assertFalse(spanExporter.getFinishedSpanItems().isEmpty());
    }

    @BeforeEach
    void setUp() {
        spanExporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();
        openTelemetrySdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        GlobalOpenTelemetry.resetForTest();
        GlobalOpenTelemetry.set(openTelemetrySdk);
    }

    @AfterEach
    void tearDown() {
        GlobalOpenTelemetry.resetForTest();
        openTelemetrySdk.close();
        tracerProvider.close();
    }
}
