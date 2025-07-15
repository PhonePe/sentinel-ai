package configuredagents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.phonepe.sentinelai.core.agent.*;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import lombok.SneakyThrows;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 *
 */
@SuppressWarnings("unused")
public class ConfiguredAgent {

    private final String name;
    private final String description;
    private final RootAgent rootAgent;
    private final JsonNode inputSchema;
    private final JsonNode outputSchema;

    public static final class RootAgent extends Agent<String, String, RootAgent> {

        private final String name;
        private final JsonNode outputSchema;

        public RootAgent(
                final String name,
                final String prompt,
                final JsonNode outputSchema,
                final List<AgentExtension<String, String, RootAgent>> extensions,
                final ToolBox toolBox) {
            super(String.class,
                    prompt,
                    AgentSetup.builder().build(),
                    extensions,
                    Map.of());
            this.name = name;
            this.outputSchema = outputSchema;
            this.registerToolbox(toolBox);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        protected JsonNode outputSchema() {
            return outputSchema;
        }

        @Override
        protected String translateData(
                ModelOutput modelOutput,
                AgentSetup mergedAgentSetup) throws JsonProcessingException {
            return mergedAgentSetup.getMapper()
                    .writeValueAsString(modelOutput.getData());
        }
    }

    public ConfiguredAgent(
            final String name,
            String description,
            final String prompt,
            final List<AgentExtension<String, String, RootAgent>> rootAgentExtensions,
            final ToolBox availableTools,
            JsonNode inputSchema,
            JsonNode outputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.outputSchema = outputSchema;
        this.rootAgent = new RootAgent(name, prompt, outputSchema, rootAgentExtensions, availableTools);
    }

    @SneakyThrows
    public final CompletableFuture<AgentOutput<JsonNode>> executeAsync(AgentInput<JsonNode> input) {
        final var mapper = input.getAgentSetup().getMapper();
        return rootAgent.executeAsync(new AgentInput<>(
                                              mapper.writeValueAsString(input.getRequest()),
                                              input.getFacts(),
                                              input.getRequestMetadata(),
                                              input.getOldMessages(),
                                              input.getAgentSetup()
                                      )
                                     )
                .thenApply(output -> {
                    try {
                        final var json = Objects.requireNonNullElseGet(mapper, JsonUtils::createMapper)
                                .readTree(output.getData());
                        return new AgentOutput<>(json,
                                                 output.getNewMessages(),
                                                 output.getAllMessages(),
                                                 output.getUsage(),
                                                 output.getError());
                    }
                    catch (Exception e) {
                        throw new RuntimeException("Failed to parse AgentOutput response to JsonNode", e);
                    }
                });
    }
}
