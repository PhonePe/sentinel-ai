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

import com.phonepe.sentinelai.core.utils.ToolUtils;
import com.phonepe.sentinelai.evals.tests.expectations.Operator;
import com.phonepe.sentinelai.evals.tests.expectations.OrderedExpectation;
import com.phonepe.sentinelai.evals.tests.expectations.OutputContainsExpectation;
import com.phonepe.sentinelai.evals.tests.expectations.OutputEqualsExpectation;
import com.phonepe.sentinelai.evals.tests.expectations.ToolCalledExpectation;
import com.phonepe.sentinelai.evals.tests.expectations.jsonpath.OutputJsonPathCompareExpectation;
import com.phonepe.sentinelai.evals.tests.metrics.MetricExpectation;

import lombok.val;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
        private final String jsonPath;

        private JsonPathExpectationBuilder(String jsonPath) {
            this.jsonPath = jsonPath;
        }

        /**
         * Creates an equality comparison against the configured JSONPath.
         *
         * @param expectedValue expected value at the JSONPath
         * @return JSONPath expectation using {@link Operator#EQ}
         */
        public Expectation<R, T> eq(Object expectedValue) {
            return new OutputJsonPathCompareExpectation<>(jsonPath,
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
            return new OutputJsonPathCompareExpectation<>(jsonPath,
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
            return new OutputJsonPathCompareExpectation<>(jsonPath, Operator.GTE, expectedValue);
        }

        /**
         * Creates a membership comparison against the configured JSONPath.
         *
         * @param expectedValues collection of acceptable values
         * @return JSONPath expectation using {@link Operator#IN}
         */
        public Expectation<R, T> in(Collection<?> expectedValues) {
            return new OutputJsonPathCompareExpectation<>(jsonPath,
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
            return new OutputJsonPathCompareExpectation<>(jsonPath,
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
            return new OutputJsonPathCompareExpectation<>(jsonPath,
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
            return new OutputJsonPathCompareExpectation<>(jsonPath,
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
            return new OutputJsonPathCompareExpectation<>(jsonPath,
                                                          Operator.NOT_IN,
                                                          expectedValues);
        }
    }


    /**
     * Creates an answer-relevance expectation backed by the built-in LLM judge metric.
     *
     * @param promptTemplate custom judge prompt template; {@code null} uses the default prompt
     * @param threshold      minimum score required to pass
     * @param <T>            input/request type
     * @return thresholded answer-relevance expectation
     */
    public static <T> Expectation<String, T> answerRelevance(String promptTemplate,
                                                             double threshold) {
        return new MetricExpectation<>(Metrics.answerRelevance(promptTemplate), threshold);
    }

    /**
     * Creates an answer-relevance expectation using the default judge prompt.
     *
     * @param threshold minimum score required to pass
     * @param <T>       input/request type
     * @return thresholded answer-relevance expectation
     */
    public static <T> Expectation<String, T> answerRelevance(double threshold) {
        return answerRelevance(null, threshold);
    }

    /**
     * Alias for {@link #where(String)}.
     *
     * @param jsonPath JSONPath expression to evaluate
     * @param <R>      result/output type
     * @param <T>      input/request type
     * @return JSONPath expectation builder
     */
    public static <R, T> JsonPathExpectationBuilder<R, T> at(String jsonPath) {
        return where(jsonPath);
    }

    /**
     * Creates a JSONPath equality expectation.
     *
     * @param jsonPath      JSONPath expression to evaluate
     * @param expectedValue expected value at that path
     * @param <R>           result/output type
     * @param <T>           input/request type
     * @return equality-based JSONPath expectation
     */
    public static <R, T> Expectation<R, T> jsonPathEquals(String jsonPath,
                                                          Object expectedValue) {
        return new OutputJsonPathCompareExpectation<>(jsonPath,
                                                      Operator.EQ,
                                                      expectedValue);
    }

    /**
     * Creates an ordered message expectation from an existing list.
     *
     * @param expectations ordered message expectations to satisfy
     * @param <R>          result/output type
     * @param <T>          input/request type
     * @return ordered expectation definition
     */
    public static <R, T> Expectation<R, T> ordered(List<MessageExpectation<R, T>> expectations) {
        return new OrderedExpectation<>(expectations);
    }

    @SafeVarargs
    /**
     * Creates an ordered message expectation from varargs.
     *
     * @param expectations ordered message expectations to satisfy
     * @param <R>          result/output type
     * @param <T>          input/request type
     * @return ordered expectation definition
     */
    public static <R, T> Expectation<R, T> ordered(MessageExpectation<R, T>... expectations) {
        return new OrderedExpectation<>(Arrays.asList(expectations));
    }

    /**
     * Creates a substring containment expectation for string outputs.
     *
     * @param substring substring that should appear in the output
     * @param <T>       input/request type
     * @return output-contains expectation
     */
    public static <T> Expectation<String, T> outputContains(String substring) {
        return new OutputContainsExpectation<>(substring);
    }

    /**
     * Creates an equality expectation for the full output value.
     *
     * @param expected expected output value
     * @param <R>      result/output type
     * @param <T>      input/request type
     * @return output-equals expectation
     */
    public static <R, T> Expectation<R, T> outputEquals(R expected) {
        return new OutputEqualsExpectation<>(expected);
    }

    /**
     * Creates a similarity expectation without an explicit threshold.
     * The embedding model is provided at execution time via
     * {@link com.phonepe.sentinelai.evals.tests.metrics.MetricExecutorRegistry}.
     *
     * @param referenceText reference answer to compare with
     * @param <T>           input/request type
     * @return metric-backed similarity expectation
     */
    public static <T> Expectation<String, T> outputSimilarity(String referenceText) {
        return new MetricExpectation<>(Metrics.outputSimilarity(referenceText));
    }

    /**
     * Creates a similarity expectation with an explicit threshold.
     * The embedding model is provided at execution time via
     * {@link com.phonepe.sentinelai.evals.tests.metrics.MetricExecutorRegistry}.
     *
     * @param referenceText reference answer to compare with
     * @param threshold     minimum similarity score required to pass
     * @param <T>           input/request type
     * @return thresholded similarity expectation
     */
    public static <T> Expectation<String, T> outputSimilarity(String referenceText,
                                                              double threshold) {
        return new MetricExpectation<>(Metrics.outputSimilarity(referenceText), threshold);
    }

    /**
     * Creates a tool-call expectation by deriving the tool id from a tool method.
     *
     * @param method tool method annotated with Sentinel tool metadata
     * @param <R>    result/output type
     * @param <T>    input/request type
     * @return expectation asserting the tool was called once
     */
    public static <R, T> MessageExpectation<R, T> toolCalled(Method method) {
        return toolCalled(method, 1);
    }

    /**
     * Creates a tool-call expectation by deriving the tool id from a tool method.
     *
     * @param method tool method annotated with Sentinel tool metadata
     * @param times  expected number of invocations
     * @param <R>    result/output type
     * @param <T>    input/request type
     * @return expectation asserting the derived tool id was called the requested number of times
     */
    public static <R, T> MessageExpectation<R, T> toolCalled(Method method, int times) {
        val metadata = ToolUtils.toolMetadata(method.getDeclaringClass().getSimpleName(), method);
        return new ToolCalledExpectation<>(metadata.getFirst().getId(), times, null);
    }

    /**
     * Creates a tool-call expectation for a named tool with a default count of one.
     *
     * @param toolName tool identifier to look for in the message history
     * @param <R>      result/output type
     * @param <T>      input/request type
     * @return tool-call expectation
     */
    public static <R, T> MessageExpectation<R, T> toolCalled(String toolName) {
        return new ToolCalledExpectation<>(toolName);
    }

    /**
     * Creates a tool-call expectation for a named tool and expected call count.
     *
     * @param toolName tool identifier to look for in the message history
     * @param times    expected number of invocations
     * @param <R>      result/output type
     * @param <T>      input/request type
     * @return tool-call expectation
     */
    public static <R, T> MessageExpectation<R, T> toolCalled(String toolName,
                                                             int times) {
        return new ToolCalledExpectation<>(toolName, times, null);
    }

    /**
     * Creates a tool-call expectation for a named tool, count, and exact parameter map.
     *
     * @param toolName       tool identifier to look for in the message history
     * @param times          expected number of invocations
     * @param expectedParams expected arguments payload parsed as a map
     * @param <R>            result/output type
     * @param <T>            input/request type
     * @return tool-call expectation
     */
    public static <R, T> MessageExpectation<R, T> toolCalled(String toolName,
                                                             int times,
                                                             Map<String, Object> expectedParams) {
        return new ToolCalledExpectation<>(toolName, times, expectedParams);
    }

    /**
     * Starts building a JSONPath-based expectation.
     *
     * @param jsonPath JSONPath expression to evaluate
     * @param <R>      result/output type
     * @param <T>      input/request type
     * @return JSONPath expectation builder
     */
    public static <R, T> JsonPathExpectationBuilder<R, T> where(String jsonPath) {
        return new JsonPathExpectationBuilder<>(jsonPath);
    }
}
