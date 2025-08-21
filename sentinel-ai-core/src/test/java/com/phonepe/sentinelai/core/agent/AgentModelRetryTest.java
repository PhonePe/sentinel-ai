package com.phonepe.sentinelai.core.agent;

import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.errors.SentinelError;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.outputvalidation.OutputValidator;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.ToolRunApprovalSeeker;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class AgentModelRetryTest {
    private static final class TestAgent extends Agent<String, String, TestAgent> {

        @Builder
        TestAgent(
                @NonNull AgentSetup setup,
                List<AgentExtension<String, String, TestAgent>> agentExtensions,
                Map<String, ExecutableTool> knownTools,
                ToolRunApprovalSeeker<String, String, TestAgent> toolRunApprovalSeeker,
                OutputValidator<String, String> outputValidator) {
            super(String.class, "blah", setup, agentExtensions, knownTools, toolRunApprovalSeeker, outputValidator);
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
                .thenReturn(CompletableFuture.completedFuture(ModelOutput.error(List.of(), new ModelUsageStats(), SentinelError.error(
                        ErrorType.NO_RESPONSE, "Test error"))));
        final var agent = TestAgent.builder()
                .setup(AgentSetup.builder()
                               .mapper(JsonUtils.createMapper())
                               .model(model)
                               .build())
                .build();
        final var response = agent.executeAsync(AgentInput.<String>builder()
                                   .request("Hello")
                                   .build())
                .get();
        System.out.println(response);

    }
}
