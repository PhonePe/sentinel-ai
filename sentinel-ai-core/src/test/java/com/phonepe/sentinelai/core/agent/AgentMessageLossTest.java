package com.phonepe.sentinelai.core.agent;

import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.errors.SentinelError;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
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
        final var output = mapper.createObjectNode().textNode("Success response");

        when(model.compute(any(), anyCollection(), anyList(), anyMap(), any(ToolRunner.class), any(), anyList()))
                .thenAnswer((Answer<CompletableFuture<ModelOutput>>) invocation -> {
                    List<?> messages = invocation.getArgument(2);
                    int currentCall = callCount.incrementAndGet();

                    // On every call, messages should NOT be empty. 
                    // It should contain at least SystemPrompt and UserPrompt.
                    if (messages.isEmpty()) {
                        throw new IllegalStateException("Messages list is empty on call " + currentCall);
                    }

                    if (currentCall == 1) {
                        // Return error on first call to trigger retry
                        return CompletableFuture.completedFuture(
                                ModelOutput.error(invocation.getArgument(2), // Passing the same messages list
                                                  new ModelUsageStats(),
                                                  SentinelError.error(ErrorType.NO_RESPONSE, "First attempt failed")));
                    }

                    return CompletableFuture.completedFuture(
                            ModelOutput.success(mapper.createObjectNode().set(Agent.OUTPUT_VARIABLE_NAME, output),
                                                List.of(),
                                                invocation.getArgument(2),
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
                                                        .build())
                .get();

        assertNotNull(response.getData());
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertEquals(2, callCount.get());
    }
}
