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

package com.phonepe.sentinelai.core.compaction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactionPromptsTest {

    @Test
    void testBuilder() {
        final var customSystemPrompt = "Custom system prompt";
        final var customUserPrompt = "Custom user prompt";
        final var customSchema = "{}";

        final var prompts = CompactionPrompts.builder()
                .summarizationSystemPrompt(customSystemPrompt)
                .summarizationUserPrompt(customUserPrompt)
                .promptSchema(customSchema)
                .build();

        assertEquals(customSystemPrompt, prompts.getSummarizationSystemPrompt());
        assertEquals(customUserPrompt, prompts.getSummarizationUserPrompt());
        assertEquals(customSchema, prompts.getPromptSchema());
    }

    @Test
    void testBuilderDefaultValues() {
        final var prompts = CompactionPrompts.builder().build();

        assertEquals(CompactionPrompts.DEFAULT_SUMMARIZATION_SYSTEM_PROMPT,
                     prompts.getSummarizationSystemPrompt());
        assertEquals(CompactionPrompts.DEFAULT_SUMMARIZATION_USER_PROMPT, prompts.getSummarizationUserPrompt());
        assertEquals(CompactionPrompts.DEFAULT_PROMPT_SCHEMA, prompts.getPromptSchema());
    }

    @Test
    void testDefaultConstants() {
        assertNotNull(CompactionPrompts.DEFAULT_SUMMARIZATION_SYSTEM_PROMPT);
        assertNotNull(CompactionPrompts.DEFAULT_SUMMARIZATION_USER_PROMPT);
        assertNotNull(CompactionPrompts.DEFAULT_PROMPT_SCHEMA);
        assertFalse(CompactionPrompts.DEFAULT_SUMMARIZATION_SYSTEM_PROMPT.isEmpty());
        assertFalse(CompactionPrompts.DEFAULT_SUMMARIZATION_USER_PROMPT.isEmpty());
        assertFalse(CompactionPrompts.DEFAULT_PROMPT_SCHEMA.isEmpty());
    }

    @Test
    void testDefaultInstance() {
        assertNotNull(CompactionPrompts.DEFAULT);
        assertEquals(CompactionPrompts.DEFAULT_SUMMARIZATION_SYSTEM_PROMPT,
                     CompactionPrompts.DEFAULT.getSummarizationSystemPrompt());
        assertEquals(CompactionPrompts.DEFAULT_SUMMARIZATION_USER_PROMPT,
                     CompactionPrompts.DEFAULT.getSummarizationUserPrompt());
        assertEquals(CompactionPrompts.DEFAULT_PROMPT_SCHEMA, CompactionPrompts.DEFAULT.getPromptSchema());
    }

    @Test
    void testSchemaContainsRequiredFields() {
        assertTrue(CompactionPrompts.DEFAULT_PROMPT_SCHEMA.contains("\"title\""));
        assertTrue(CompactionPrompts.DEFAULT_PROMPT_SCHEMA.contains("\"summary\""));
        assertTrue(CompactionPrompts.DEFAULT_PROMPT_SCHEMA.contains("\"keywords\""));
    }

    @Test
    void testSystemPromptContainsTokenBudgetPlaceholder() {
        assertTrue(CompactionPrompts.DEFAULT_SUMMARIZATION_SYSTEM_PROMPT.contains("${tokenBudget}"));
    }

    @Test
    void testUserPromptContainsSessionMessagesPlaceholder() {
        assertTrue(CompactionPrompts.DEFAULT_SUMMARIZATION_USER_PROMPT.contains("${sessionMessages}"));
    }

    @Test
    void testWithPromptSchema() {
        final var newSchema = "{\"type\": \"object\"}";
        final var updated = CompactionPrompts.DEFAULT.withPromptSchema(newSchema);

        assertEquals(newSchema, updated.getPromptSchema());
    }

    @Test
    void testWithSystemPrompt() {
        final var newPrompt = "New system prompt";
        final var updated = CompactionPrompts.DEFAULT.withSummarizationSystemPrompt(newPrompt);

        assertEquals(newPrompt, updated.getSummarizationSystemPrompt());
        assertEquals(CompactionPrompts.DEFAULT_SUMMARIZATION_USER_PROMPT, updated.getSummarizationUserPrompt());
    }

    @Test
    void testWithUserPrompt() {
        final var newPrompt = "New user prompt";
        final var updated = CompactionPrompts.DEFAULT.withSummarizationUserPrompt(newPrompt);

        assertEquals(newPrompt, updated.getSummarizationUserPrompt());
        assertEquals(CompactionPrompts.DEFAULT_SUMMARIZATION_SYSTEM_PROMPT,
                     updated.getSummarizationSystemPrompt());
    }
}
