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

package com.phonepe.sentinelai.filesystem.skills;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillContentSanitizerTest {

    @Test
    void testSanitizePreservesCodeFenceContent() {
        final var sanitized = SkillContentSanitizer.sanitize("""
                ```bash
                // keep this
                echo hi
                ```
                """);

        assertTrue(sanitized.contains("// keep this"));
        assertTrue(sanitized.contains("echo hi"));
    }

    @Test
    void testSanitizeRemovesHtmlAndStandaloneSlashComments() {
        final var sanitized = SkillContentSanitizer.sanitize("""
                <!-- hidden -->
                Keep this
                // remove this
                """);

        assertFalse(sanitized.contains("hidden"));
        assertFalse(sanitized.contains("remove this"));
        assertTrue(sanitized.contains("Keep this"));
    }

    @Test
    void testSanitizeRemovesInlineAndMultilineHtmlComments() {
        final var sanitized = SkillContentSanitizer.sanitize("""
                keep <!-- inline --> line
                <!--
                hidden block
                -->
                keep2
                """);

        assertTrue(sanitized.contains("keep  line"));
        assertTrue(sanitized.contains("keep2"));
        assertFalse(sanitized.contains("inline"));
        assertFalse(sanitized.contains("hidden block"));
    }
}
