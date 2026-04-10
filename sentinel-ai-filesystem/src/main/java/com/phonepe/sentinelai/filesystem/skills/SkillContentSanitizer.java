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

import java.util.regex.Pattern;

public final class SkillContentSanitizer {

    private static final Pattern MARKDOWN_FENCE_PATTERN = Pattern.compile("^\\s*(```|~~~).*$");
    private static final Pattern DOUBLE_SLASH_COMMENT_LINE = Pattern.compile("^\\s*//.*$");

    private SkillContentSanitizer() {
    }

    public static String sanitize(final String content) {
        if (content == null || content.isBlank()) {
            return content;
        }

        final var lines = content.split("\\n", -1);
        final var output = new StringBuilder();
        boolean inCodeFence = false;
        boolean inHtmlComment = false;

        for (final var line : lines) {
            if (MARKDOWN_FENCE_PATTERN.matcher(line).matches()) {
                inCodeFence = !inCodeFence;
                appendLine(output, line);
                continue;
            }

            if (inCodeFence) {
                appendLine(output, line);
                continue;
            }

            final var stripped = stripHtmlComments(line, inHtmlComment);
            inHtmlComment = stripped.inHtmlComment();
            final var sanitizedLine = stripped.line();

            if (sanitizedLine == null || sanitizedLine.isBlank()) {
                continue;
            }

            if (DOUBLE_SLASH_COMMENT_LINE.matcher(sanitizedLine).matches()) {
                continue;
            }

            appendLine(output, sanitizedLine);
        }

        return output.toString().trim();
    }

    private static void appendLine(final StringBuilder output, final String line) {
        if (output.length() > 0) {
            output.append('\n');
        }
        output.append(line);
    }

    private static StrippedLine stripHtmlComments(final String line, final boolean inHtmlComment) {
        final var output = new StringBuilder();
        boolean commentOpen = inHtmlComment;
        int index = 0;

        while (index < line.length()) {
            if (commentOpen) {
                final var commentEnd = line.indexOf("-->", index);
                if (commentEnd == -1) {
                    return new StrippedLine(output.toString(), true);
                }
                index = commentEnd + 3;
                commentOpen = false;
                continue;
            }

            final var commentStart = line.indexOf("<!--", index);
            if (commentStart == -1) {
                output.append(line, index, line.length());
                break;
            }

            output.append(line, index, commentStart);
            index = commentStart + 4;
            commentOpen = true;
        }

        return new StrippedLine(output.toString(), commentOpen);
    }

    private record StrippedLine(
            String line,
            boolean inHtmlComment
    ) {
    }
}
