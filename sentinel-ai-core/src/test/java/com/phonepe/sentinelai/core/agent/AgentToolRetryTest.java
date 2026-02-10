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

package com.phonepe.sentinelai.core.agent;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.earlytermination.NeverTerminateEarlyStrategy;
import com.phonepe.sentinelai.core.errorhandling.DefaultErrorHandler;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.outputvalidation.DefaultOutputValidator;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.ExternalTool;
import com.phonepe.sentinelai.core.tools.InternalTool;
import com.phonepe.sentinelai.core.tools.ToolDefinition;
import com.phonepe.sentinelai.core.tools.ToolMethodInfo;
import com.phonepe.sentinelai.core.utils.JsonUtils;

import lombok.NonNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentToolRetryTest {

    public static class TestTool {
        private final AtomicInteger callCount = new AtomicInteger(0);

        public String failingMethod() {
            if (callCount.getAndIncrement() < 2) {
                throw new RuntimeException("Temporary failure");
            }
            return "Success";
        }

        public String slowMethod() throws InterruptedException {
            Thread.sleep(2000);
            return "Too late";
        }
    }

    private static final class TestAgent extends Agent<String, String, TestAgent> {

        TestAgent(@NonNull AgentSetup setup,
                  Map<String, ExecutableTool> knownTools) {
            super(String.class,
                  "irrelevant",
                  setup,
                  List.of(),
                  knownTools,
                  new ApproveAllToolRuns<>(),
                  new DefaultOutputValidator<>(),
                  new DefaultErrorHandler<>(),
                  new NeverTerminateEarlyStrategy());
        }

        @Override
        public String name() {
            return "test-agent";
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRetryLogic() throws Exception {
        final var setup = AgentSetup.builder()
                .mapper(JsonUtils.createMapper())
                .eventBus(new EventBus())
                .build();
        final var agent = new TestAgent(setup, Map.of());
        final var approvalSeeker = new ApproveAllToolRuns<String, String, TestAgent>();
        final var context = mock(AgentRunContext.class);
        when(context.getAgentSetup()).thenReturn(setup);
        when(context.getRunId()).thenReturn("run-id");

        final var toolRunner = new AgentToolRunner<String, String, TestAgent>(agent,
                                                                              setup,
                                                                              approvalSeeker,
                                                                              context);

        final var callCount = new AtomicInteger(0);
        final var toolDefinition = ToolDefinition.builder()
                .id("test-tool")
                .name("testTool")
                .description("Test tool")
                .retries(3)
                .timeoutSeconds(30)
                .build();

        final var externalTool = new ExternalTool(toolDefinition,
                                                  null,
                                                  (ctx, name, args) -> {
                                                      if (callCount
                                                              .getAndIncrement() < 2) {
                                                          return new ExternalTool.ExternalToolResponse("Temporary failure",
                                                                                                       ErrorType.TOOL_CALL_TEMPORARY_FAILURE);
                                                      }
                                                      return new ExternalTool.ExternalToolResponse("Success",
                                                                                                   ErrorType.SUCCESS);
                                                  });

        final var toolCall = ToolCall.builder()
                .runId("run-id")
                .toolCallId("call-id")
                .toolName("testTool")
                .arguments("{}")
                .build();

        final var response = toolRunner.runTool(Map.of("testTool",
                                                       externalTool), toolCall);

        assertEquals(ErrorType.SUCCESS, response.getErrorType());
        assertEquals("\"Success\"", response.getResponse());
        assertEquals(3, callCount.get()); // 2 failures + 1 success
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTimeoutLogic() throws Exception {
        final var setup = AgentSetup.builder()
                .mapper(JsonUtils.createMapper())
                .eventBus(new EventBus())
                .build();
        final var agent = new TestAgent(setup, Map.of());
        final var approvalSeeker = new ApproveAllToolRuns<String, String, TestAgent>();
        final var context = mock(AgentRunContext.class);
        when(context.getAgentSetup()).thenReturn(setup);
        when(context.getRunId()).thenReturn("run-id");

        final var toolRunner = new AgentToolRunner<String, String, TestAgent>(agent,
                                                                              setup,
                                                                              approvalSeeker,
                                                                              context);

        final var testTool = new TestTool();
        final var method = TestTool.class.getMethod("slowMethod");
        final var toolDefinition = ToolDefinition.builder()
                .id("test-tool")
                .name("testTool")
                .description("Test tool")
                .retries(0)
                .timeoutSeconds(1)
                .build();
        final var internalTool = new InternalTool(toolDefinition,
                                                  new ToolMethodInfo(List.of(),
                                                                     method,
                                                                     String.class),
                                                  testTool);

        final var toolCall = ToolCall.builder()
                .runId("run-id")
                .toolCallId("call-id")
                .toolName("testTool")
                .arguments("{}")
                .build();
        final var response = toolRunner.runTool(Map.of("testTool",
                                                       internalTool),
                                                toolCall);
        assertEquals(ErrorType.TOOL_CALL_TIMEOUT, response.getErrorType());
    }
}
