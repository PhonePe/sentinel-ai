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

package com.phonepe.sentinelai.evals.tests;

import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.embedding.EmbeddingModel;
import com.phonepe.sentinelai.evals.tests.expectations.OrderedExpectation;
import com.phonepe.sentinelai.evals.tests.expectations.OutputContainsExpectation;
import com.phonepe.sentinelai.evals.tests.expectations.OutputEqualsExpectation;
import com.phonepe.sentinelai.evals.tests.expectations.ToolCalledExpectation;
import com.phonepe.sentinelai.evals.tests.expectations.jsonpath.Operator;
import com.phonepe.sentinelai.evals.tests.expectations.jsonpath.OutputJsonPathCompareExpectation;
import com.phonepe.sentinelai.evals.tests.metrics.MetricExpectation;

import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@UtilityClass
public class Expectations {
    public static class JsonPathExpectationBuilder<R, T> {
        private final String jsonPath;

        private JsonPathExpectationBuilder(String jsonPath) {
            this.jsonPath = jsonPath;
        }

        public Expectation<R, T> eq(Object expectedValue) {
            return new com.phonepe.sentinelai.evals.tests.expectations.jsonpath.OutputJsonPathCompareExpectation<>(jsonPath,
                                                                                                                   Operator.EQ,
                                                                                                                   expectedValue);
        }

        public Expectation<R, T> gt(Object expectedValue) {
            return new com.phonepe.sentinelai.evals.tests.expectations.jsonpath.OutputJsonPathCompareExpectation<>(jsonPath,
                                                                                                                   Operator.GT,
                                                                                                                   expectedValue);
        }

        public Expectation<R, T> gte(Object expectedValue) {
            return new OutputJsonPathCompareExpectation<>(jsonPath, Operator.GTE, expectedValue);
        }

        public Expectation<R, T> in(Collection<?> expectedValues) {
            return new com.phonepe.sentinelai.evals.tests.expectations.jsonpath.OutputJsonPathCompareExpectation<>(jsonPath,
                                                                                                                   Operator.IN,
                                                                                                                   expectedValues);
        }

        public Expectation<R, T> lt(Object expectedValue) {
            return new com.phonepe.sentinelai.evals.tests.expectations.jsonpath.OutputJsonPathCompareExpectation<>(jsonPath,
                                                                                                                   Operator.LT,
                                                                                                                   expectedValue);
        }

        public Expectation<R, T> lte(Object expectedValue) {
            return new com.phonepe.sentinelai.evals.tests.expectations.jsonpath.OutputJsonPathCompareExpectation<>(jsonPath,
                                                                                                                   Operator.LTE,
                                                                                                                   expectedValue);
        }

        public Expectation<R, T> ne(Object expectedValue) {
            return new com.phonepe.sentinelai.evals.tests.expectations.jsonpath.OutputJsonPathCompareExpectation<>(jsonPath,
                                                                                                                   Operator.NE,
                                                                                                                   expectedValue);
        }

        public Expectation<R, T> notIn(Collection<?> expectedValues) {
            return new com.phonepe.sentinelai.evals.tests.expectations.jsonpath.OutputJsonPathCompareExpectation<>(jsonPath,
                                                                                                                   Operator.NOT_IN,
                                                                                                                   expectedValues);
        }
    }


    public static <T> Expectation<String, T> answerRelevance(Model evaluatorModel,
                                                             String promptTemplate,
                                                             double threshold) {
        return new MetricExpectation<>(Metrics.answerRelevance(evaluatorModel, promptTemplate), threshold);
    }

    public static <T> Expectation<String, T> answerRelevance(Model evaluatorModel,
                                                             double threshold) {
        return answerRelevance(evaluatorModel, null, threshold);
    }

    public static <R, T> JsonPathExpectationBuilder<R, T> at(String jsonPath) {
        return where(jsonPath);
    }

    public static <R, T> Expectation<R, T> jsonPathEquals(String jsonPath,
                                                          Object expectedValue) {
        return new com.phonepe.sentinelai.evals.tests.expectations.jsonpath.OutputJsonPathCompareExpectation<>(jsonPath,
                                                                                                               Operator.EQ,
                                                                                                               expectedValue);
    }

    public static <R, T> Expectation<R, T> ordered(List<MessageExpectation<R, T>> expectations) {
        return new OrderedExpectation<>(expectations);
    }

    @SafeVarargs
    public static <R, T> Expectation<R, T> ordered(MessageExpectation<R, T>... expectations) {
        return new OrderedExpectation<>(Arrays.asList(expectations));
    }

    public static <T> Expectation<String, T> outputContains(String substring) {
        return new OutputContainsExpectation<>(substring);
    }

    public static <R, T> Expectation<R, T> outputEquals(R expected) {
        return new OutputEqualsExpectation<>(expected);
    }

    public static <T> Expectation<String, T> outputSimilarity(EmbeddingModel embeddingModel,
                                                              String referenceText) {
        return new MetricExpectation<>(Metrics.outputSimilarity(embeddingModel, referenceText));
    }

    public static <T> Expectation<String, T> outputSimilarity(EmbeddingModel embeddingModel,
                                                              String referenceText,
                                                              double threshold) {
        return new MetricExpectation<>(Metrics.outputSimilarity(embeddingModel, referenceText), threshold);
    }

    public static <R, T> MessageExpectation<R, T> toolCalled(String toolName) {
        return new ToolCalledExpectation<>(toolName);
    }

    public static <R, T> MessageExpectation<R, T> toolCalled(String toolName,
                                                             int times) {
        return new ToolCalledExpectation<>(toolName, times, null);
    }

    public static <R, T> MessageExpectation<R, T> toolCalled(String toolName,
                                                             int times,
                                                             Map<String, Object> expectedParams) {
        return new ToolCalledExpectation<>(toolName, times, expectedParams);
    }

    public static <R, T> JsonPathExpectationBuilder<R, T> where(String jsonPath) {
        return new JsonPathExpectationBuilder<>(jsonPath);
    }
}
