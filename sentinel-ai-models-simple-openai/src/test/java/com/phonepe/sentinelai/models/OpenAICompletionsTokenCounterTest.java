package com.phonepe.sentinelai.models;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.GenericText;
import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAICompletionsTokenCounterTest {

    private OpenAICompletionsTokenCounter tokenCounter;
    private Encoding encoder;

    @BeforeEach
    void setUp() {
        tokenCounter = new OpenAICompletionsTokenCounter();
        EncodingRegistry encodingRegistry = Encodings.newDefaultEncodingRegistry();
        encoder = encodingRegistry.getEncoding(EncodingType.CL100K_BASE);
    }

    @Test
    void testEstimateTokenCountEmptyMessages() {
        assertEquals(TokenCountingConfig.DEFAULT.getMessageOverHead(), tokenCounter.estimateTokenCount(List.of(), TokenCountingConfig.DEFAULT, EncodingType.CL100K_BASE));
    }

    @Test
    void testEstimateTokenCountSystemPrompt() {
        final var content = "You are a helpful assistant.";
        SystemPrompt systemPrompt = new SystemPrompt("s1", "r1", content, false, null);

        int expected = TokenCountingConfig.DEFAULT.getAssistantPrimingOverhead() +
                TokenCountingConfig.DEFAULT.getMessageOverHead() +
                countTokens("SYSTEM") +
                countTokens(content);

        assertEquals(expected, tokenCounter.estimateTokenCount(List.of(systemPrompt), TokenCountingConfig.DEFAULT, EncodingType.CL100K_BASE));
    }

    @Test
    void testEstimateTokenCountUserPrompt() {
        final var content = "Hello, how are you?";
        UserPrompt userPrompt = new UserPrompt("s1", "r1", content, LocalDateTime.now());

        int expected = TokenCountingConfig.DEFAULT.getAssistantPrimingOverhead() +
                TokenCountingConfig.DEFAULT.getMessageOverHead() +
                countTokens("USER") +
                countTokens(content);

        assertEquals(expected, tokenCounter.estimateTokenCount(List.of(userPrompt), TokenCountingConfig.DEFAULT, EncodingType.CL100K_BASE));
    }

    @Test
    void testEstimateTokenCountAssistantTextResponse() {
        final var content = "I am fine, thank you!";
        Text assistantResponse = new Text("s1", "r1", content);

        int expected = TokenCountingConfig.DEFAULT.getAssistantPrimingOverhead() +
                TokenCountingConfig.DEFAULT.getMessageOverHead() +
                countTokens("ASSISTANT") +
                countTokens(content);

        assertEquals(expected, tokenCounter.estimateTokenCount(List.of(assistantResponse), TokenCountingConfig.DEFAULT, EncodingType.CL100K_BASE));
    }

    @Test
    void testEstimateTokenCountStructuredOutput() {
        final var content = "{\"answer\": \"fine\"}";
        StructuredOutput structuredOutput = new StructuredOutput("s1", "r1", content);

        int expected = TokenCountingConfig.DEFAULT.getAssistantPrimingOverhead() +
                TokenCountingConfig.DEFAULT.getMessageOverHead() +
                countTokens("ASSISTANT") +
                countTokens(content);

        assertEquals(expected, tokenCounter.estimateTokenCount(List.of(structuredOutput), TokenCountingConfig.DEFAULT, EncodingType.CL100K_BASE));
    }

    @Test
    void testEstimateTokenCountToolCall() {
        final var toolName = "get_weather";
        final var arguments = "{\"location\": \"Bangalore\"}";
        final var toolCallId = "call_123";
        ToolCall toolCall = new ToolCall("s1", "r1", toolCallId, toolName, arguments);

        int expected = TokenCountingConfig.DEFAULT.getAssistantPrimingOverhead() +
                TokenCountingConfig.DEFAULT.getMessageOverHead() +
                countTokens("ASSISTANT") +
                countTokens(toolCallId) +
                countTokens(toolName) +
                TokenCountingConfig.DEFAULT.getFormattingOverhead() +
                countTokens(arguments) +
                countTokens("null"); // Content is null in ToolCall, Objects.toString(null) is "null"

        assertEquals(expected, tokenCounter.estimateTokenCount(List.of(toolCall), TokenCountingConfig.DEFAULT, EncodingType.CL100K_BASE));
    }

    @Test
    void testEstimateTokenCountToolCallResponse() {
        final var response = "Cloudy with a chance of meatballs";
        final var toolCallId = "call_123";
        final var toolName = "get_weather";
        ToolCallResponse toolCallResponse = new ToolCallResponse("s1", "r1", toolCallId, toolName, ErrorType.SUCCESS,
                response, LocalDateTime.now());

        int expected = TokenCountingConfig.DEFAULT.getAssistantPrimingOverhead() +
                TokenCountingConfig.DEFAULT.getMessageOverHead() +
                countTokens("TOOL") +
                TokenCountingConfig.DEFAULT.getFormattingOverhead() +
                countTokens(toolCallId) +
                countTokens(response);

        assertEquals(expected, tokenCounter.estimateTokenCount(List.of(toolCallResponse), TokenCountingConfig.DEFAULT, EncodingType.CL100K_BASE));
    }

    @Test
    void testEstimateTokenCountGenericText() {
        final var content = "Some generic text";
        GenericText genericText = new GenericText("s1", "r1", AgentGenericMessage.Role.USER, content);

        int expected = TokenCountingConfig.DEFAULT.getAssistantPrimingOverhead() +
                TokenCountingConfig.DEFAULT.getMessageOverHead() +
                countTokens("USER") +
                countTokens(content);

        assertEquals(expected, tokenCounter.estimateTokenCount(List.of(genericText), TokenCountingConfig.DEFAULT, EncodingType.CL100K_BASE));
    }

    @Test
    void testEstimateTokenCountMultipleMessages() {
        SystemPrompt systemPrompt = new SystemPrompt("s1", "r1", "System", false, null);
        UserPrompt userPrompt = new UserPrompt("s1", "r1", "User", LocalDateTime.now());

        int expected = TokenCountingConfig.DEFAULT.getAssistantPrimingOverhead() +
                (TokenCountingConfig.DEFAULT.getMessageOverHead() + countTokens("SYSTEM") + countTokens("System")) +
                (TokenCountingConfig.DEFAULT.getMessageOverHead() + countTokens("USER") + countTokens("User"));

        assertEquals(expected, tokenCounter.estimateTokenCount(List.of(systemPrompt, userPrompt), TokenCountingConfig.DEFAULT, EncodingType.CL100K_BASE));
    }

    private int countTokens(final String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        return encoder.encodeOrdinary(content).size();
    }
}
