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

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.outputvalidation.OutputValidationResults;
import com.phonepe.sentinelai.evals.EvalEngine;
import com.phonepe.sentinelai.evals.tests.Dataset;
import com.phonepe.sentinelai.evals.tests.TestCase;
import com.phonepe.sentinelai.evals.tests.expectations.Operator;
import com.phonepe.sentinelai.evals.tests.expectations.jsonpath.OutputJsonPathCompareExpectation;
import com.phonepe.sentinelai.examples.texttosql.agent.TextToSqlAgent;
import com.phonepe.sentinelai.examples.texttosql.cli.support.StubTextToSqlModel;
import com.phonepe.sentinelai.examples.texttosql.tools.model.SqlQueryResult;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
class TextToSqlCliEvalTest {
    @Test
    void evalRunPassesForCliStyleTextToSqlAgent() {
        final var mapper = new ObjectMapper();
        final var setup = AgentSetup.builder()
                .mapper(mapper)
                .model(new StubTextToSqlModel())
                .build();

        final var agent = new TextToSqlAgent(
                                             setup,
                                             List.of(),
                                             (context, output) -> OutputValidationResults.success());

        final Dataset<String, SqlQueryResult> dataset = new Dataset<>(
                                                                      "text-to-sql-cli-evals",
                                                                      List.of(new TestCase<>(
                                                                                             "show me one row",
                                                                                             List.of(
                                                                                                     new OutputJsonPathCompareExpectation<>(
                                                                                                                                            "$.generatedSql",
                                                                                                                                            Operator.EQ,
                                                                                                                                            "SELECT 1"
                                                                                                     ),
                                                                                                     new OutputJsonPathCompareExpectation<>(
                                                                                                                                            "$.results",
                                                                                                                                            Operator.EQ,
                                                                                                                                            List.of("{\"answer\":1}")
                                                                                                     )
                                                                                             )
                                                                      )));

        final var report = new EvalEngine(mapper).run(dataset, agent);

        log.info("Eval report: {}", report);
        assertEquals(1, report.getExecutedTestCases());
        assertEquals(1, report.getPassedTestCases());
        assertEquals(0, report.getFailedTestCases());
        assertEquals(0, report.getSkippedTestCases());
    }
}
