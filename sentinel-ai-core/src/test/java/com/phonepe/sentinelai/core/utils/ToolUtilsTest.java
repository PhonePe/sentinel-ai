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

package com.phonepe.sentinelai.core.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.tools.ToolMethodInfo;
import com.phonepe.sentinelai.core.tools.ToolParameter;

import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolUtilsTest {

    private static final String SESSION_ID = "session-1";
    private static final String RUN_ID = "run-1";

    @SuppressWarnings("unused")
    static class TestTools {
        public void listTool(List<String> items) {
        }

        public void stringTool(String value) {
        }
    }

    @Value
    private static class ConvertCase {
        String name;
        String methodName;
        Class<?> parameterType;
        String json;
        Object expected;
    }

    @Value
    private static class FallBackCase {
        String name;
        String json;
    }

    private static Stream<ConvertCase> convertCases() {
        return Stream.of(
                         new ConvertCase("string-encoded array is coerced to a list for a collection param",
                                         "listTool",
                                         List.class,
                                         "{\"items\": \"[\\\"first\\\", \\\"second\\\"]\"}",
                                         List.of("first", "second")),
                         new ConvertCase("plain array is passed through for a collection param",
                                         "listTool",
                                         List.class,
                                         "{\"items\": [\"first\", \"second\"]}",
                                         List.of("first", "second")),
                         new ConvertCase("string param is not coerced even when it holds a JSON array",
                                         "stringTool",
                                         String.class,
                                         "{\"value\": \"[\\\"not coerced\\\"]\"}",
                                         "[\"not coerced\"]"));
    }

    private static Stream<FallBackCase> fallBackCases() {
        return Stream.of(
                         new FallBackCase("text is valid JSON but not an array",
                                          "{\"items\": \"{\\\"key\\\": \\\"value\\\"}\"}"),
                         new FallBackCase("text is not valid JSON",
                                          "{\"items\": \"not json at all\"}"));
    }

    private static ToolMethodInfo methodInfoFor(String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        final var method = TestTools.class.getDeclaredMethod(methodName, parameterTypes);
        final var parameters = new ArrayList<ToolParameter>();
        for (var i = 0; i < method.getParameters().length; i++) {
            final var parameter = method.getParameters()[i];
            parameters.add(new ToolParameter(parameter.getName(),
                                             "",
                                             TypeFactory.defaultInstance()
                                                     .constructType(method.getGenericParameterTypes()[i])));
        }
        return new ToolMethodInfo(parameters, method, method.getReturnType());
    }

    private static ToolCall toolCall(String toolCallId, String toolName) {
        return ToolCall.builder()
                .sessionId(SESSION_ID)
                .runId(RUN_ID)
                .toolCallId(toolCallId)
                .toolName(toolName)
                .arguments("{}")
                .build();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("convertCases")
    void convertParams(ConvertCase testCase) throws Exception {
        final var mapper = new ObjectMapper();
        final var methodInfo = methodInfoFor(testCase.getMethodName(), testCase.getParameterType());

        final var params = ToolUtils.convertToRealParams(methodInfo, testCase.getJson(), mapper);

        assertEquals(1, params.size());
        assertEquals(testCase.getExpected(), params.get(0));
    }

    @ParameterizedTest(name = "[{index}] falls back to the raw node when the {0}")
    @MethodSource("fallBackCases")
    void fallsBackToRawNode(FallBackCase testCase) throws Exception {
        final var mapper = new ObjectMapper();
        final var methodInfo = methodInfoFor("listTool", List.class);

        // The raw textual node is kept and the pre-existing conversion failure surfaces.
        assertThrows(IllegalArgumentException.class,
                     () -> ToolUtils.convertToRealParams(methodInfo, testCase.getJson(), mapper));
    }

    @Test
    void processUnhandledExceptionPreservesNullSessionIdWhenProvided() {
        final var toolCall = ToolCall.builder()
                .runId(RUN_ID)
                .toolCallId("call-5")
                .toolName("tool")
                .arguments("{}")
                .build();

        final ToolCallResponse result = ToolUtils.processUnhandledException(null, RUN_ID, toolCall, "err");

        assertEquals(ErrorType.TOOL_CALL_TEMPORARY_FAILURE, result.getErrorType());
        assertEquals("call-5", result.getToolCallId());
    }

    @Test
    void processUnhandledExceptionWithExceptionExtractsRootCauseMessage() {
        final var toolCall = toolCall("call-1", "myTool");
        final var cause = new IllegalStateException("root problem");
        final var wrapped = new RuntimeException("wrapper", cause);

        final var result = ToolUtils.processUnhandledException(SESSION_ID, RUN_ID, toolCall, wrapped);

        assertEquals(ErrorType.TOOL_CALL_TEMPORARY_FAILURE, result.getErrorType());
        assertEquals("call-1", result.getToolCallId());
        assertEquals("myTool", result.getToolName());
        assertEquals(SESSION_ID, result.getSessionId());
        assertEquals(RUN_ID, result.getRunId());
        assertTrue(result.getResponse().contains("root problem"));
        assertFalse(result.isSuccess());
    }

    @Test
    void processUnhandledExceptionWithExceptionUsesExceptionMessageWhenNoCause() {
        final var toolCall = toolCall("call-2", "simpleTool");
        final var exception = new RuntimeException("direct message");

        final var result = ToolUtils.processUnhandledException(SESSION_ID, RUN_ID, toolCall, exception);

        assertEquals(ErrorType.TOOL_CALL_TEMPORARY_FAILURE, result.getErrorType());
        assertTrue(result.getResponse().contains("direct message"));
    }

    @Test
    void processUnhandledExceptionWithStringBuildsExpectedResponse() {
        final var toolCall = toolCall("call-3", "anotherTool");

        final var result = ToolUtils.processUnhandledException(SESSION_ID, RUN_ID, toolCall, "something failed");

        assertEquals(ErrorType.TOOL_CALL_TEMPORARY_FAILURE, result.getErrorType());
        assertEquals("call-3", result.getToolCallId());
        assertEquals("anotherTool", result.getToolName());
        assertEquals(SESSION_ID, result.getSessionId());
        assertEquals(RUN_ID, result.getRunId());
        assertTrue(result.getResponse().contains("something failed"));
        assertNotNull(result.getSentAt());
        assertFalse(result.isSuccess());
    }

    @Test
    void processUnhandledExceptionWithStringPrefixesErrorRunningTool() {
        final var toolCall = toolCall("call-4", "tool");
        final var errorMessage = "unexpected null";

        final var result = ToolUtils.processUnhandledException(SESSION_ID, RUN_ID, toolCall, errorMessage);

        assertTrue(result.getResponse().startsWith("Error running tool:"));
        assertTrue(result.getResponse().contains(errorMessage));
    }

    @Test
    void testToolCallWithtEmptyArguments() {
        //This is aa trivial test to ensure that the ToolCall builder can handle
        //null or empty arguments without throwing an exception and sets a default value
        assertEquals(ToolCall.EMPTY_ARGUMENTS,
                     ToolCall.builder()
                             .sessionId(SESSION_ID)
                             .runId(RUN_ID)
                             .toolCallId("call-6")
                             .toolName("tool")
                             .build().getArguments());
        assertEquals(ToolCall.EMPTY_ARGUMENTS,
                     ToolCall.builder()
                             .sessionId(SESSION_ID)
                             .runId(RUN_ID)
                             .toolCallId("call-6")
                             .toolName("tool")
                             .arguments("")
                             .build().getArguments());
    }

}
