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

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.With;

@Value
@Builder
@With
@AllArgsConstructor(access = AccessLevel.PUBLIC)
public class CompactionPrompts {
    public static final String DEFAULT_SUMMARIZATION_SYSTEM_PROMPT = """
            You are a prompt compactor. Rewrite the provided prompt material to be concise, unambiguous, and semantically equivalent. Preserve all safety rules, constraints, priorities, and required output specifications. Do not add new instructions or remove any critical ones. Maintain the original hierarchy and ordering of priorities; keep numbered/ordered lists when present. Keep proper nouns and safety/legal language intact. Remove redundancy, filler, hedging, and repeated phrasing. Prefer direct, imperative wording. Do not change schema or label names. Avoid reformatting unless it improves clarity without altering meaning.

            Target length: ${tokenBudget} tokens. If the original cannot be safely compacted under this limit, prioritize semantic preservation and produce the shortest faithful version.

            Output requirement: Return only the compacted text with no commentary, headers, or code fences.""";

    public static final String DEFAULT_SUMMARIZATION_USER_PROMPT = """
            Compact the following messages while preserving semantics and priorities.

            Messages to compact (JSON array of chat turns): ${sessionMessages}
            Format: [{"type":"chat","role":"system|user|assistant","content":"…"}, {"type":"tool_call", "toolCallId" :"…", "toolName":"…", "arguments":{…}}, {"type" : "tool_call_response", "toolCallId": "", "result":"…"}]

            Instructions:

                - Produce a single compacted prompt suitable as a system message for downstream use.
                - Incorporate only enduring, directive-relevant content from the messages (e.g., persistent constraints, goals, required formats); omit chit-chat and ephemeral details.
                - Preserve any safety/legal language and output-format requirements exactly.
                - Keep ordering and priority intact; convert rambling text into succinct bullet points or numbered rules when helpful.
                - Target length: ${tokenBudget} tokens.

            Return only the compacted prompt text.""";

    public static final String DEFAULT_PROMPT_SCHEMA = """
            {
                "type": "object",
                    "description": "Schema for a compact summary envelope used in prompt/response compaction workflows.",
                    "additionalProperties": false,
                    "required": [ "title", "keywords", "summary", "key_points", "key_facts", "action_items",
                    "citations", "sentiment", "confidence", "metadata" ],
                    "properties": {
                        "title": {
                            "type": "string",
                            "description": "Short, human-readable heading that captures the main topic or outcome. Aim for 3–10 words, sentence case, no trailing period."
                        },
                        "keywords": {
                            "type": "array",
                            "description": "Relevant one-word keywords or topics discussed. Keep the count to maximum 3 and minimum 1.",
                            "items": {
                                "type": "string",
                                "description": "A single keyword or topic."
                            }
                        },
                        "summary": {
                            "type": "string",
                            "description": "Concise narrative capturing the essence, context, and important outcomes. Prefer 2–5 sentences, neutral tone, no speculation, no metadata."
                        },
                        "key_points": {
                            "type": "array",
                            "description": "Distilled major points or takeaways, ordered by importance or chronology, non-redundant.",
                            "items": {
                                "type": "string",
                                "description": "A single, clear point (one idea per item)."
                            }
                        },
                        "key_facts": {
                            "type": "array",
                            "description": "Objective, verifiable facts extracted from the source (names, dates, numbers, direct quotes). Favor extractive phrasing and include units where relevant.",
                            "items": {
                                "type": "string",
                                "description": "One factual statement or exact quote; include measurements, counts, or timestamps when applicable."
                            }
                        },
                        "action_items": {
                            "type": "array",
                            "description": "Concrete next steps or decisions. Use imperative phrasing; optionally include owner and due date.",
                            "items": {
                                "type": "string",
                                "description": "One actionable task (e.g., 'Draft proposal by Friday', 'Alice to review PR #123')."
                            }
                        },
                        "citations": {
                            "type": "array",
                            "description": "Source references supporting facts or quotes. Useful for traceability and verification.",
                            "items": {
                                "type": "object",
                                "description": "A single citation entry linking content to its source.",
                                "additionalProperties": false,
                                "required": [ "source", "quote" ],
                                "properties": {
                                    "source": {
                                        "type": "string",
                                        "description": "Stable identifier for the source (e.g., URL, document title, repo path, message ID)."
                                    },
                                    "quote": {
                                        "type": "string",
                                        "description": "Optional exact excerpt or snippet from the source to support verification."
                                    }
                                }
                            }
                        },
                        "sentiment": {
                            "type": "string",
                            "description": "Overall tone of the content or outcome.",
                            "enum": [ "positive", "neutral", "negative" ]
                        },
                        "confidence": {
                            "type": "number",
                            "description": "Model-assessed confidence (0–10) in the accuracy and completeness of the summary. Heuristic, not statistically calibrated.",
                            "minimum": 0,
                            "maximum": 10
                        },
                        "metadata": {
                            "type": "string",
                            "description": "Free-form auxiliary details (e.g., model name/version, timestamp, prompt hash, processing notes). Content should not duplicate other fields."
                        }
                }
            }
                                            """;

    public static final CompactionPrompts DEFAULT = new CompactionPrompts(DEFAULT_SUMMARIZATION_SYSTEM_PROMPT,
                                                                          DEFAULT_SUMMARIZATION_USER_PROMPT,
                                                                          DEFAULT_PROMPT_SCHEMA);

    @Builder.Default
    String summarizationSystemPrompt = DEFAULT_SUMMARIZATION_SYSTEM_PROMPT;

    @Builder.Default
    String summarizationUserPrompt = DEFAULT_SUMMARIZATION_USER_PROMPT;

    @Builder.Default
    String promptSchema = DEFAULT_PROMPT_SCHEMA;

}
