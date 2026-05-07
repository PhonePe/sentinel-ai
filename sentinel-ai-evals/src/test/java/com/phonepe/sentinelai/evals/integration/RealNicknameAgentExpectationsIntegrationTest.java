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

package com.phonepe.sentinelai.evals.integration;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.sashirestela.cleverclient.client.OkHttpClientAdapter;
import io.github.sashirestela.openai.SimpleOpenAI;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.utils.EnvLoader;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.embedding.EmbeddingModel;
import com.phonepe.sentinelai.evals.EvalEngine;
import com.phonepe.sentinelai.evals.junit.assertions.EvalReportAssertions;
import com.phonepe.sentinelai.evals.tests.Dataset;
import com.phonepe.sentinelai.evals.tests.Expectation;
import com.phonepe.sentinelai.evals.tests.ExpectationExecutorRegistry;
import com.phonepe.sentinelai.evals.tests.Expectations;
import com.phonepe.sentinelai.evals.tests.TestCase;
import com.phonepe.sentinelai.evals.tests.metrics.MetricExecutorRegistry;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;
import com.phonepe.sentinelai.models.SimpleOpenAIModelOptions;

import lombok.NonNull;
import lombok.SneakyThrows;
import okhttp3.OkHttpClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Real integration test that runs only when live endpoint properties are enabled via -Preal-tests.
 */
class RealNicknameAgentExpectationsIntegrationTest {

    private static class FixedEmbeddingModel implements EmbeddingModel {
        @Override
        public void close() {
            // no-op
        }

        @Override
        public int dimensions() {
            return 2;
        }

        @Override
        public float[] getEmbedding(String input) {
            if (input == null || input.isBlank()) {
                return new float[]{
                        0f, 0f
                };
            }
            final var normalized = input.toLowerCase();
            final var nicknameSignal = normalized.contains("nickname") ? 1f : 0f;
            final var nameSignal = normalized.contains("shubham") ? 1f : 0f;
            return new float[]{
                    nicknameSignal, nameSignal
            };
        }
    }

    private static class NicknameObjectAgent extends Agent<UserProfile, NicknameResponse, NicknameObjectAgent> {
        protected NicknameObjectAgent(@NonNull AgentSetup setup) {
            super(NicknameResponse.class,
                  """
                          You are a nickname assistant.
                          Steps:
                          1) Call tool nickname_style_guide exactly once.
                          2) Call tool build_nickname_candidates exactly once using the request name and age.
                          3) Return final output exactly matching the object returned by build_nickname_candidates.
                          """,
                  setup,
                  List.of(),
                  Map.of());
        }

        @Tool(name = "build_nickname_candidates", value = "Build deterministic nickname response")
        public NicknameResponse buildNicknameCandidates(String name,
                                                        int age) {

            final var nicknames = List.of(
                                          name + "y",
                                          name + "ster",
                                          name.substring(0, Math.min(4, name.length())) + "z");
            final var primary = nicknames.get(0);
            return new NicknameResponse(name,
                                        age,
                                        nicknames,
                                        primary,
                                        nicknames.size(),
                                        "friendly",
                                        "nickname options generated for " + name);
        }

        @Override
        public String name() {
            return "real-nickname-object-agent";
        }

        @Tool(name = "nickname_style_guide", value = "Returns nickname style constraints")
        public String nicknameStyleGuide() {
            return "Use friendly and short nicknames";
        }
    }

    private static class NicknameStringAgent extends Agent<UserProfile, String, NicknameStringAgent> {
        protected NicknameStringAgent(@NonNull AgentSetup setup) {
            super(String.class,
                  """
                          You are a nickname assistant.
                          Call tool real-nickname-string-agent_build_nickname_candidates once and return exactly the same summary text as tool output.
                          """,
                  setup,
                  List.of(),
                  Map.of());
        }

        @Tool(name = "build_nickname_candidates", value = "Build deterministic nickname summary")
        public String buildNicknameCandidates(String name,
                                              int age) {
            return "Possible nicknames for %s (%d): %s, %s, %s"
                    .formatted(name,
                               age,
                               name + "y",
                               name + "ster",
                               name.substring(0, Math.min(4, name.length())) + "z");
        }

        @Override
        public String name() {
            return "real-nickname-string-agent";
        }
    }

    private static Model realModel(ObjectMapper mapper) {
        final var enabled = "true".equalsIgnoreCase(System.getProperty("sentinelai.useRealEndpoints", "false"));
        Assumptions.assumeTrue(enabled,
                               "Skipping real integration test. Enable with -Preal-tests or -Dsentinelai.useRealEndpoints=true");

        final var endpoint = EnvLoader.readEnv("OPENAI_BASE_URL",
                                               EnvLoader.readEnv("OPENAI_ENDPOINT", "https://api.openai.com"));
        final var apiKey = EnvLoader.readEnv("OPENAI_API_KEY", null);
        final var modelName = EnvLoader.readEnv("OPENAI_MODEL", "gpt-4o");

        Assumptions.assumeTrue(endpoint != null && !endpoint.isBlank(),
                               "OPENAI_BASE_URL/OPENAI_ENDPOINT is required for real tests");
        Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(), "OPENAI_API_KEY is required for real tests");

        final var provider = SimpleOpenAI.builder()
                .baseUrl(endpoint)
                .apiKey(apiKey)
                .objectMapper(mapper)
                .clientAdapter(new OkHttpClientAdapter(new OkHttpClient.Builder().build()))
                .build();

        return new SimpleOpenAIModel<>(modelName,
                                       provider,
                                       mapper,
                                       SimpleOpenAIModelOptions.builder()
                                               .toolChoice(SimpleOpenAIModelOptions.ToolChoice.AUTO)
                                               .build());
    }

    private static AgentSetup setup(Model model,
                                    ObjectMapper mapper) {
        return AgentSetup.builder()
                .mapper(mapper)
                .model(model)
                .modelSettings(ModelSettings.builder()
                        .temperature(0f)
                        .seed(7)
                        .parallelToolCalls(false)
                        .build())
                .build();
    }

    @SneakyThrows
    @Test
    void realAgentCoversAllExpectations() {
        final var mapper = JsonUtils.createMapper();
        final var judgeModel = realModel(mapper);

        final var objectAgent = new NicknameObjectAgent(setup(realModel(mapper), mapper));
        final var expectedObject = new NicknameResponse("Shubham",
                                                        26,
                                                        List.of("Shubhamy", "Shubhamster", "Shubz"),
                                                        "Shubhamy",
                                                        3,
                                                        "friendly",
                                                        "nickname options generated for Shubham");

        final List<Expectation<NicknameResponse, UserProfile>> objectExpectations = List.of(
                                                                                            Expectations.outputEquals(
                                                                                                                      expectedObject),
                                                                                            Expectations.jsonPathEquals(
                                                                                                                        "$.originalName",
                                                                                                                        "Shubham"),
                                                                                            Expectations
                                                                                                    .<NicknameResponse, UserProfile>where(
                                                                                                                                          "$.originalName")
                                                                                                    .eq("Shubham"),
                                                                                            Expectations
                                                                                                    .<NicknameResponse, UserProfile>where(
                                                                                                                                          "$.styleHint")
                                                                                                    .ne("formal"),
                                                                                            Expectations
                                                                                                    .<NicknameResponse, UserProfile>where(
                                                                                                                                          "$.nicknameCount")
                                                                                                    .gt(2),
                                                                                            Expectations
                                                                                                    .<NicknameResponse, UserProfile>where(
                                                                                                                                          "$.nicknameCount")
                                                                                                    .gte(3),
                                                                                            Expectations
                                                                                                    .<NicknameResponse, UserProfile>where(
                                                                                                                                          "$.nicknameCount")
                                                                                                    .lt(5),
                                                                                            Expectations
                                                                                                    .<NicknameResponse, UserProfile>where(
                                                                                                                                          "$.nicknameCount")
                                                                                                    .lte(3),
                                                                                            Expectations
                                                                                                    .<NicknameResponse, UserProfile>where(
                                                                                                                                          "$.primaryNickname")
                                                                                                    .in(List.of("Shubhamy",
                                                                                                                "Shubz")),
                                                                                            Expectations
                                                                                                    .<NicknameResponse, UserProfile>where(
                                                                                                                                          "$.primaryNickname")
                                                                                                    .notIn(List.of(
                                                                                                                   "Boss",
                                                                                                                   "Chief")),
                                                                                            Expectations.toolCalled(
                                                                                                                    NicknameObjectAgent.class
                                                                                                                            .getMethod("buildNicknameCandidates",
                                                                                                                                       String.class,
                                                                                                                                       int.class),
                                                                                                                    1),
                                                                                            Expectations.toolCalled(
                                                                                                                    "buildNicknameCandidates",
                                                                                                                    1,
                                                                                                                    Map.of("name",
                                                                                                                           "Shubham",
                                                                                                                           "age",
                                                                                                                           26)),
                                                                                            Expectations.ordered(
                                                                                                                 Expectations
                                                                                                                         .toolCalled(
                                                                                                                                     NicknameObjectAgent.class
                                                                                                                                             .getMethod("nicknameStyleGuide")
                                                                                                                         ),
                                                                                                                 Expectations
                                                                                                                         .toolCalled(
                                                                                                                                     NicknameObjectAgent.class
                                                                                                                                             .getMethod("buildNicknameCandidates",
                                                                                                                                                        String.class,
                                                                                                                                                        int.class))));

        final var objectDataset = new Dataset<>("real-nickname-object-expectations",
                                                List.of(new TestCase<>(new UserProfile("Shubham",
                                                                                       26),
                                                                       objectExpectations)));

        final var objectReport = new EvalEngine().run(objectDataset, objectAgent);

        assertEquals(1, objectReport.getExecutedTestCases());
        EvalReportAssertions.assertNoFailures(objectReport);
        assertEquals(objectExpectations.size(),
                     objectReport.getTestCaseReports().get(0).getExpectationReports().size());

        final var stringAgent = new NicknameStringAgent(setup(realModel(mapper), mapper));
        final var embeddingModel = new FixedEmbeddingModel();

        final List<Expectation<String, UserProfile>> stringExpectations = List.of(
                                                                                  Expectations.outputContains(
                                                                                                              "Possible nicknames"),
                                                                                  Expectations.outputEquals(
                                                                                                            "Possible nicknames for Shubham (26): Shubhamy, Shubhamster, Shubz"),
                                                                                  Expectations.outputSimilarity(
                                                                                                                embeddingModel,
                                                                                                                "Possible nicknames for Shubham (26): Shubhamy, Shubhamster, Shubz",
                                                                                                                0.9),
                                                                                  Expectations.outputSimilarity(
                                                                                                                embeddingModel,
                                                                                                                "Possible nicknames for Shubham (26): Shubhamy, Shubhamster, Shubz"),
                                                                                  Expectations.answerRelevance(0.5));

        final var stringDataset = new Dataset<>("real-nickname-string-expectations",
                                                List.of(new TestCase<>(new UserProfile("Shubham",
                                                                                       26),
                                                                       stringExpectations)));

        final var stringReport = new EvalEngine(mapper,
                                                ExpectationExecutorRegistry.withDefaults(
                                                                                         MetricExecutorRegistry
                                                                                                 .withDefaults(judgeModel)))
                .run(stringDataset,
                     stringAgent);

        assertEquals(1, stringReport.getExecutedTestCases());
        EvalReportAssertions.assertNoFailures(stringReport);
        assertEquals(stringExpectations.size(),
                     stringReport.getTestCaseReports().get(0).getExpectationReports().size());
    }

    private record NicknameResponse(
            String originalName,
            int age,
            List<String> nicknames,
            String primaryNickname,
            int nicknameCount,
            String styleHint,
            String summary
    ) {
    }

    private record UserProfile(
            String name,
            int age
    ) {
    }
}
