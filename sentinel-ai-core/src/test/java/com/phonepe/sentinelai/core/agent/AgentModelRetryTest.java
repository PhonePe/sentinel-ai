package com.phonepe.sentinelai.core.agent;

import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.errorhandling.ErrorResponseHandler;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.errors.SentinelError;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.outputvalidation.OutputValidationResults;
import com.phonepe.sentinelai.core.outputvalidation.OutputValidator;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.ToolRunApprovalSeeker;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests detailed functionality in {@link Agent}
 */
class AgentModelRetryTest {
    private static final class TestAgent extends Agent<String, String, TestAgent> {

        @Builder
        TestAgent(
                @NonNull AgentSetup setup,
                List<AgentExtension<String, String, TestAgent>> agentExtensions,
                Map<String, ExecutableTool> knownTools,
                ToolRunApprovalSeeker<String, String, TestAgent> toolRunApprovalSeeker,
                OutputValidator<String, String> outputValidator,
                ErrorResponseHandler<String> errorHandler) {
            super(String.class,
                  "blah",
                  setup,
                  agentExtensions,
                  knownTools,
                  toolRunApprovalSeeker,
                  outputValidator,
                  errorHandler);
        }

        @Override
        public String name() {
            return "test-agent";
        }
    }

    @Test
    @SneakyThrows
    void testNoResponse() {
        final var model = mock(Model.class);
        when(model.compute(any(),
                           anyCollection(),
                           anyList(),
                           anyMap(),
                           any(ToolRunner.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        ModelOutput.error(List.of(),
                                          new ModelUsageStats(),
                                          SentinelError.error(ErrorType.NO_RESPONSE, "Test error"))));
        final var agent = TestAgent.builder()
                .setup(AgentSetup.builder()
                               .mapper(JsonUtils.createMapper())
                               .model(model)
                               .retrySetup(RetrySetup.builder()
                                                   .delayAfterFailedAttempt(Duration.ofMillis(100))
                                                   .stopAfterAttempt(1)
                                                   .build())
                               .build())
                .build();
        final var response = agent.executeAsync(AgentInput.<String>builder()
                                                        .request("Hello")
                                                        .build())
                .get();
        assertNull(response.getData());
        assertEquals(ErrorType.NO_RESPONSE, response.getError().getErrorType());
    }

    @Test
    @SneakyThrows
    void testRetryFailureAndThenSuccess() {
        final var model = mock(Model.class);
        final var callCount = new AtomicInteger(0);
        final var mapper = JsonUtils.createMapper();
        final var output = mapper.createObjectNode().textNode("Hi!!");
        when(model.compute(any(),
                           anyCollection(),
                           anyList(),
                           anyMap(),
                           any(ToolRunner.class)))
                .thenAnswer((Answer<CompletableFuture<ModelOutput>>) invocationOnMock -> {
                    if (callCount.getAndIncrement() < 2) {
                        return CompletableFuture.completedFuture(
                                ModelOutput.error(List.of(),
                                                  new ModelUsageStats(),
                                                  SentinelError.error(ErrorType.NO_RESPONSE, "Test error")));
                    }
                    return CompletableFuture.completedFuture(
                            ModelOutput.success(mapper.createObjectNode().set(Agent.OUTPUT_VARIABLE_NAME, output),
                                                List.of(),
                                                List.of(),
                                                new ModelUsageStats()));
                });
        final var agent = TestAgent.builder()
                .setup(AgentSetup.builder()
                               .mapper(JsonUtils.createMapper())
                               .model(model)
                               .retrySetup(RetrySetup.builder()
                                                   .delayAfterFailedAttempt(Duration.ofMillis(100))
                                                   .stopAfterAttempt(3)
                                                   .build())
                               .build())
                .build();
        final var response = agent.executeAsync(AgentInput.<String>builder()
                                                        .request("Hello")
                                                        .build())
                .get();
        assertNotNull(response.getData());
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
    }

    @Test
    @SneakyThrows
    void testException() {
        final var model = mock(Model.class);
        when(model.compute(any(),
                           anyCollection(),
                           anyList(),
                           anyMap(),
                           any(ToolRunner.class)))
                .thenThrow(new IllegalArgumentException("Test error"));
        final var agent = TestAgent.builder()
                .setup(AgentSetup.builder()
                               .mapper(JsonUtils.createMapper())
                               .model(model)
                               .retrySetup(RetrySetup.builder()
                                                   .delayAfterFailedAttempt(Duration.ofMillis(100))
                                                   .stopAfterAttempt(1)
                                                   .build())
                               .build())
                .build();
        final var response = agent.executeAsync(AgentInput.<String>builder()
                                                        .request("Hello")
                                                        .build())
                .get();
        assertNull(response.getData());
        assertEquals(ErrorType.GENERIC_MODEL_CALL_FAILURE, response.getError().getErrorType());
    }

    @Test
    @SneakyThrows
    void testRetryValidationFailureAndThenSuccess() {
        final var model = mock(Model.class);
        final var callCount = new AtomicInteger(0);
        final var mapper = JsonUtils.createMapper();
        final var output = mapper.createObjectNode().textNode("Hi!!");
        when(model.compute(any(),
                           anyCollection(),
                           anyList(),
                           anyMap(),
                           any(ToolRunner.class)))
                .thenAnswer((Answer<CompletableFuture<ModelOutput>>) invocationOnMock -> {
                    if (callCount.getAndIncrement() < 2) {
                        return CompletableFuture.completedFuture(
                                ModelOutput.success(mapper.createObjectNode()
                                                            .set(Agent.OUTPUT_VARIABLE_NAME,
                                                                 mapper.createObjectNode().textNode("")),
                                                    List.of(),
                                                    List.of(),
                                                    new ModelUsageStats()));
                    }
                    return CompletableFuture.completedFuture(
                            ModelOutput.success(mapper.createObjectNode().set(Agent.OUTPUT_VARIABLE_NAME, output),
                                                List.of(),
                                                List.of(),
                                                new ModelUsageStats()));
                });
        final var agent = TestAgent.builder()
                .setup(AgentSetup.builder()
                               .mapper(JsonUtils.createMapper())
                               .model(model)
                               .retrySetup(RetrySetup.builder()
                                                   .delayAfterFailedAttempt(Duration.ofMillis(100))
                                                   .stopAfterAttempt(3)
                                                   .build())
                               .build())
                .outputValidator((context, strOutput) -> Strings.isNullOrEmpty(strOutput)
                                                         ? OutputValidationResults.failure(
                        "Empty output is not acceptable")
                                                         : OutputValidationResults.success())
                .build();
        final var response = agent.executeAsync(AgentInput.<String>builder()
                                                        .request("Hello")
                                                        .build())
                .get();
        assertNotNull(response.getData());
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
    }

    @Test
    @SneakyThrows
    void testErrorHandlerDrivenRetry() {
        final var model = mock(Model.class);
        final var callCount = new AtomicInteger(0);
        final var mapper = JsonUtils.createMapper();
        final var output = mapper.createObjectNode().textNode("Hi!!");
        final var retryCalled = new AtomicInteger(0);
        when(model.compute(any(),
                           anyCollection(),
                           anyList(),
                           anyMap(),
                           any(ToolRunner.class)))
                .thenAnswer((Answer<CompletableFuture<ModelOutput>>) invocationOnMock -> {
                    callCount.incrementAndGet();
                    return CompletableFuture.completedFuture(
                            ModelOutput.success(mapper.createObjectNode().set(Agent.OUTPUT_VARIABLE_NAME, output),
                                                List.of(),
                                                List.of(),
                                                new ModelUsageStats()));
                });
        final var agent = TestAgent.builder()
                .setup(AgentSetup.builder()
                               .mapper(JsonUtils.createMapper())
                               .model(model)
                               .retrySetup(RetrySetup.builder()
                                                   .delayAfterFailedAttempt(Duration.ofMillis(100))
                                                   .stopAfterAttempt(3)
                                                   .build())
                               .build())
                .errorHandler(new ErrorResponseHandler<String>() {
                    @Override
                    public <U> AgentOutput<U> handle(AgentRunContext<String> context, AgentOutput<U> agentOutput) {
                        if (retryCalled.getAndIncrement() < 2) {
                            return AgentOutput.error(agentOutput.getAllMessages(),
                                                     context.getOldMessages(),
                                                     agentOutput.getUsage(),
                                                     SentinelError.error(ErrorType.GENERIC_MODEL_CALL_FAILURE, "Test error"));
                        }
                        return agentOutput;
                    }
                })
                .build();
        final var response = agent.executeAsync(AgentInput.<String>builder()
                                                        .request("Hello")
                                                        .build())
                .get();
        assertNotNull(response.getData());
        assertEquals(ErrorType.SUCCESS, response.getError().getErrorType());
        assertEquals(3, callCount.get());
    }
}
