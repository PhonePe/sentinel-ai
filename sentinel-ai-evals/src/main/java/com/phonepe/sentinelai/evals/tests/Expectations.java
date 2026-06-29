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

import com.phonepe.sentinelai.evals.tests.expectations.Operator;
import com.phonepe.sentinelai.evals.tests.expectations.OrderedExpectation;
import com.phonepe.sentinelai.evals.tests.expectations.OutputCompareExpectation;
import com.phonepe.sentinelai.evals.tests.expectations.jsonpath.OutputJsonPathCompareExpectation;
import com.phonepe.sentinelai.evals.tests.metrics.MetricExpectation;

import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Factory helpers for creating built-in expectation definitions.
 */
@UtilityClass
public class Expectations {
    /**
     * Fluent builder for JSONPath-based expectations.
     *
     * @param <R> result/output type
     * @param <T> input/request type
     */
    public static class JsonPathExpectationBuilder<R, T> {
        private final String id;
        private final String jsonPath;

        private JsonPathExpectationBuilder(String id, String jsonPath) {
            this.id = id;
            this.jsonPath = jsonPath;
        }

        /**
         * Creates an equality comparison against the configured JSONPath.
         *
         * @param expectedValue expected value at the JSONPath
         * @return JSONPath expectation using {@link Operator#EQ}
         */
        public Expectation<R, T> eq(Object expectedValue) {
            return new OutputJsonPathCompareExpectation<>(id,
                                                          jsonPath,
                                                          Operator.EQ,
                                                          expectedValue);
        }

        /**
         * Creates a greater-than comparison against the configured JSONPath.
         *
         * @param expectedValue lower bound value
         * @return JSONPath expectation using {@link Operator#GT}
         */
        public Expectation<R, T> gt(Object expectedValue) {
            return new OutputJsonPathCompareExpectation<>(id,
                                                          jsonPath,
                                                          Operator.GT,
                                                          expectedValue);
        }

        /**
         * Creates a greater-than-or-equal comparison against the configured JSONPath.
         *
         * @param expectedValue lower bound value
         * @return JSONPath expectation using {@link Operator#GTE}
         */
        public Expectation<R, T> gte(Object expectedValue) {
            return new OutputJsonPathCompareExpectation<>(id, jsonPath, Operator.GTE, expectedValue);
        }

        /**
         * Creates a membership comparison against the configured JSONPath.
         *
         * @param expectedValues collection of acceptable values
         * @return JSONPath expectation using {@link Operator#IN}
         */
        public Expectation<R, T> in(Collection<?> expectedValues) {
            return new OutputJsonPathCompareExpectation<>(id,
                                                          jsonPath,
                                                          Operator.IN,
                                                          expectedValues);
        }

        /**
         * Creates a less-than comparison against the configured JSONPath.
         *
         * @param expectedValue upper bound value
         * @return JSONPath expectation using {@link Operator#LT}
         */
        public Expectation<R, T> lt(Object expectedValue) {
            return new OutputJsonPathCompareExpectation<>(id,
                                                          jsonPath,
                                                          Operator.LT,
                                                          expectedValue);
        }

        /**
         * Creates a less-than-or-equal comparison against the configured JSONPath.
         *
         * @param expectedValue upper bound value
         * @return JSONPath expectation using {@link Operator#LTE}
         */
        public Expectation<R, T> lte(Object expectedValue) {
            return new OutputJsonPathCompareExpectation<>(id,
                                                          jsonPath,
                                                          Operator.LTE,
                                                          expectedValue);
        }

        /**
         * Creates a not-equals comparison against the configured JSONPath.
         *
         * @param expectedValue disallowed value
         * @return JSONPath expectation using {@link Operator#NE}
         */
        public Expectation<R, T> ne(Object expectedValue) {
            return new OutputJsonPathCompareExpectation<>(id,
                                                          jsonPath,
                                                          Operator.NE,
                                                          expectedValue);
        }

        /**
         * Creates a non-membership comparison against the configured JSONPath.
         *
         * @param expectedValues collection of disallowed values
         * @return JSONPath expectation using {@link Operator#NOT_IN}
         */
        public Expectation<R, T> notIn(Collection<?> expectedValues) {
            return new OutputJsonPathCompareExpectation<>(id,
                                                          jsonPath,
                                                          Operator.NOT_IN,
                                                          expectedValues);
        }
    }


    /**
     * Creates an answer-relevance expectation backed by the built-in LLM judge metric.
     *
     * @param id             unique identifier for this expectation
     * @param promptTemplate custom judge prompt template; {@code null} uses the default prompt
     * @param threshold      minimum score required to pass
     * @param <T>            input/request type
     * @return thresholded answer-relevance expectation
     */
    public static <T> Expectation<String, T> answerRelevance(String id,
                                                             String promptTemplate,
                                                             double threshold) {
        return new MetricExpectation<>(id, Metrics.answerRelevance(promptTemplate), threshold);
    }

    /**
     * Creates an answer-relevance expectation using the default judge prompt.
     *
     * @param id        unique identifier for this expectation
     * @param threshold minimum score required to pass
     * @param <T>       input/request type
     * @return thresholded answer-relevance expectation
     */
    public static <T> Expectation<String, T> answerRelevance(String id, double threshold) {
        return answerRelevance(id, null, threshold);
    }

    /**
     * Alias for {@link #where(String, String)}.
     *
     * @param id       unique identifier for this expectation
     * @param jsonPath JSONPath expression to evaluate
     * @param <R>      result/output type
     * @param <T>      input/request type
     * @return JSONPath expectation builder
     */
    public static <R, T> JsonPathExpectationBuilder<R, T> at(String id, String jsonPath) {
        return where(id, jsonPath);
    }

    /**
     * Creates a JSONPath equality expectation.
     *
     * @param id            unique identifier for this expectation
     * @param jsonPath      JSONPath expression to evaluate
     * @param expectedValue expected value at that path
     * @param <R>           result/output type
     * @param <T>           input/request type
     * @return equality-based JSONPath expectation
     */
    public static <R, T> Expectation<R, T> jsonPathEquals(String id,
                                                          String jsonPath,
                                                          Object expectedValue) {
        return new OutputJsonPathCompareExpectation<>(id,
                                                      jsonPath,
                                                      Operator.EQ,
                                                      expectedValue);
    }

    /**
     * Creates an ordered message expectation from an existing list.
     *
     * @param id           unique identifier for this expectation
     * @param expectations ordered message expectations to satisfy
     * @param <R>          result/output type
     * @param <T>          input/request type
     * @return ordered expectation definition
     */
    public static <R, T> Expectation<R, T> ordered(String id, List<MessageExpectation<R, T>> expectations) {
        return new OrderedExpectation<>(id, expectations);
    }

    @SafeVarargs
    /**
     * Creates an ordered message expectation from varargs.
     *
     * @param id           unique identifier for this expectation
     * @param expectations ordered message expectations to satisfy
     * @param <R>          result/output type
     * @param <T>          input/request type
     * @return ordered expectation definition
     */
    public static <R, T> Expectation<R, T> ordered(String id, MessageExpectation<R, T>... expectations) {
        return new OrderedExpectation<>(id, Arrays.asList(expectations));
    }

    /**
     * Creates a substring containment expectation for string outputs.
     *
     * <p>Backed by {@link OutputCompareExpectation} using {@link Operator#CONTAINS},
     * which performs a case-sensitive substring match.
     *
     * @param id        unique identifier for this expectation
     * @param substring substring that should appear in the output
     * @param <T>       input/request type
     * @return output-contains expectation
     */
    public static <T> Expectation<String, T> outputContains(String id, String substring) {
        return new OutputCompareExpectation<>(id, substring, Operator.CONTAINS);
    }

    /**
     * Creates an equality expectation for the full output value.
     *
     * @param id       unique identifier for this expectation
     * @param expected expected output value
     * @param <R>      result/output type
     * @param <T>      input/request type
     * @return output-equals expectation
     */
    public static <R, T> Expectation<R, T> outputEquals(String id, R expected) {
        return new OutputCompareExpectation<>(id, expected, Operator.EQ);
    }

    /**
     * Creates a similarity expectation without an explicit threshold.
     * The embedding model is provided at execution time via
     * {@link com.phonepe.sentinelai.evals.tests.metrics.MetricExecutorRegistry}.
     *
     * @param id            unique identifier for this expectation
     * @param referenceText reference answer to compare with
     * @param <T>           input/request type
     * @return metric-backed similarity expectation
     */
    public static <T> Expectation<String, T> outputSimilarity(String id, String referenceText) {
        return new MetricExpectation<>(id, Metrics.outputSimilarity(referenceText));
    }

    /**
     * Creates a similarity expectation with an explicit threshold.
     * The embedding model is provided at execution time via
     * {@link com.phonepe.sentinelai.evals.tests.metrics.MetricExecutorRegistry}.
     *
     * @param id            unique identifier for this expectation
     * @param referenceText reference answer to compare with
     * @param threshold     minimum similarity score required to pass
     * @param <T>           input/request type
     * @return thresholded similarity expectation
     */
    public static <T> Expectation<String, T> outputSimilarity(String id,
                                                              String referenceText,
                                                              double threshold) {
        return new MetricExpectation<>(id, Metrics.outputSimilarity(referenceText), threshold);
    }

    /**
     * Starts building a JSONPath-based expectation.
     *
     * @param id       unique identifier for this expectation
     * @param jsonPath JSONPath expression to evaluate
     * @param <R>      result/output type
     * @param <T>      input/request type
     * @return JSONPath expectation builder
     */
    public static <R, T> JsonPathExpectationBuilder<R, T> where(String id, String jsonPath) {
        return new JsonPathExpectationBuilder<>(id, jsonPath);
    }
}
