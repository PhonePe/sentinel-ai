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

package com.phonepe.sentinelai.models.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ResponsesProtocol#isChainBreakError(int, JsonNode)}: a chain break is a
 * 400/404 whose body names {@code previous_response_id} as the offending parameter, in the error
 * code or in the message text. Compatible gateways may flatten the error object to the top level.
 */
class ResponsesProtocolChainBreakTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private final ResponsesProtocol protocol = new ResponsesProtocol();

    static Stream<Arguments> chainBreakDetectionCases() {
        return Stream.of(Arguments.of("error code names previous_response_id",
                                      400,
                                      """
                                              {"error": {"code": "previous_response_id_not_found", \
                                              "message": "No response found."}}"""),
                         Arguments.of("error message names previous_response_id",
                                      400,
                                      """
                                              {"error": {"message": "Invalid previous_response_id: 'resp_1'."}}"""),
                         Arguments.of("error param is previous_response_id",
                                      400,
                                      """
                                              {"error": {"message": "No response found with id 'resp_1'.", \
                                              "type": "invalid_request_error", "param": "previous_response_id", \
                                              "code": "previous_response_id_not_found"}}"""),
                         Arguments.of("flattened gateway error names previous_response_id",
                                      400,
                                      """
                                              {"message": "No response found with id 'resp_1'.", \
                                              "param": "previous_response_id"}"""),
                         Arguments.of("not found status with previous_response_id code",
                                      404,
                                      """
                                              {"error": {"code": "previous_response_id_not_found"}}"""));
    }

    static Stream<Arguments> nonChainBreakCases() {
        final var paramBody = "{\"error\": {\"param\": \"previous_response_id\"}}";
        return Stream.of(Arguments.of("null error body", 400, (String) null),
                         Arguments.of("json null error body", 400, "null"),
                         Arguments.of("status 200 is not a chain break", 200, paramBody),
                         Arguments.of("status 401 is not a chain break", 401, paramBody),
                         Arguments.of("status 403 is not a chain break", 403, paramBody),
                         Arguments.of("status 409 is not a chain break", 409, paramBody),
                         Arguments.of("status 429 is not a chain break", 429, paramBody),
                         Arguments.of("status 500 is not a chain break", 500, paramBody),
                         Arguments.of("status 503 is not a chain break", 503, paramBody),
                         Arguments.of("unrelated error param",
                                      400,
                                      """
                                              {"error": {"message": "The model `gpt-4o` does not exist", \
                                              "type": "invalid_request_error", "param": "model"}}"""));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("chainBreakDetectionCases")
    void chainBreakDetected(final String name, final int status, final String errorJson) throws Exception {
        assertTrue(protocol.isChainBreakError(status, body(errorJson)), name);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonChainBreakCases")
    void notAChainBreak(final String name, final int status, final String errorJson) throws Exception {
        assertFalse(protocol.isChainBreakError(status, errorJson == null ? null : body(errorJson)), name);
    }

    private JsonNode body(final String json) throws Exception {
        return mapper.readTree(json);
    }
}
