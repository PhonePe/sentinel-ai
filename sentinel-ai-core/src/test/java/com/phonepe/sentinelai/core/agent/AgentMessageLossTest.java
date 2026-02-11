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
import org.mockito.stubbing.Answer;

import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.errors.SentinelError;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.utils.JsonUtils;

import lombok.SneakyThrows;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMessageLossTest {

    private static final class TestAgent extends Agent<String, String, TestAgent> {
        TestAgent(AgentSetup setup) {
            super(String.class, "system prompt", setup, List.of(), Map.of());
        }

        @Override
        public String name() {
            return "test-agent";
        }
    }

    @Test
    @SneakyThrows
    void testMessagePreservationOnRetry() {
        final var model = mock(Model.class);
        final var callCount = new AtomicInteger(0);
        final var mapper = JsonUtils.createMapper();
        final var output = mapper.createObjectNode()
                .textNode("Success response");

        when(model.compute(any(),
                           anyCollection(),
                           anyList(),
                           anyMap(),
                           any(ToolRunner.class),
                           any(),
                           anyList())).thenAnswer(
                                                  (Answer<CompletableFuture<ModelOutput>>) invocation -> {
                                                      List<?> messages = invocation
                                                              .getArgument(2);
                                                      int currentCall = callCount
                                                              .incrementAndGet();

                                                      // On every call, messages should NOT be empty.
                                                      // It should contain at least SystemPrompt and UserPrompt.
                                                      if (messages.isEmpty()) {
                                                          throw new IllegalStateException("Messages list is empty on call "
                                                                  + currentCall);
                                                      }

                                                      if (currentCall == 1) {
                                                          // Return error on first call to trigger retry
                                                          return CompletableFuture
                                                                  .completedFuture(ModelOutput
                                                                          .error(invocation
                                                                                  .getArgument(2), // Passing the same messages list
                                                                                 new ModelUsageStats(),
                                                                                 SentinelError
                                                                                         .error(ErrorType.NO_RESPONSE,
                                                                                                "First attempt failed")));
                                                      }

                                                      return CompletableFuture
                                                              .completedFuture(ModelOutput
                                                                      .success(mapper
                                                                              .createObjectNode()
                                                                              .set(Agent.OUTPUT_VARIABLE_NAME,
                                                                                   output),
                                                                               List.of(),
                                                                               invocation
                                                                                       .getArgument(2),
                                                                               new ModelUsageStats()));
                                                  });

        final var agent = new TestAgent(AgentSetup.builder()
                .mapper(mapper)
                .model(model)
                .retrySetup(RetrySetup.builder()
                        .delayAfterFailedAttempt(Duration.ofMillis(10))
                        .totalAttempts(2)
                        .build())
                .build());

        final var response = agent.executeAsync(AgentInput.<String>builder()
                .request("Hello")
                .build()).get();

        assertNotNull(response.getData());
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertEquals(2, callCount.get());
    }
}
