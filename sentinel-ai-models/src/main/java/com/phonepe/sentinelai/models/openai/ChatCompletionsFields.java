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

package com.phonepe.sentinelai.models.openai;

import lombok.experimental.UtilityClass;

/**
 * Field-name constants of the OpenAI Chat Completions wire format. The codec and the protocol use
 * only these constants when they build or read JSON, so no string literals appear in the wire code.
 */
@UtilityClass
public class ChatCompletionsFields {

    // Request body fields
    public static final String MODEL = "model";
    public static final String MESSAGES = "messages";
    public static final String N = "n";
    public static final String USER = "user";
    public static final String TOOLS = "tools";
    public static final String TOOL_CHOICE = "tool_choice";
    public static final String PARALLEL_TOOL_CALLS = "parallel_tool_calls";
    public static final String RESPONSE_FORMAT = "response_format";
    public static final String MAX_COMPLETION_TOKENS = "max_completion_tokens";
    public static final String TEMPERATURE = "temperature";
    public static final String TOP_P = "top_p";
    public static final String SEED = "seed";
    public static final String FREQUENCY_PENALTY = "frequency_penalty";
    public static final String PRESENCE_PENALTY = "presence_penalty";
    public static final String LOGIT_BIAS = "logit_bias";
    public static final String REASONING_EFFORT = "reasoning_effort";
    public static final String STREAM = "stream";
    // Message fields
    public static final String ID = "id";
    public static final String ROLE = "role";
    public static final String CONTENT = "content";
    public static final String TOOL_CALL_ID = "tool_call_id";
    public static final String TOOL_CALLS = "tool_calls";
    public static final String TYPE = "type";
    public static final String FUNCTION = "function";
    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String PARAMETERS = "parameters";
    public static final String STRICT = "strict";
    public static final String ARGUMENTS = "arguments";

    // Content part fields
    public static final String INPUT_AUDIO = "input_audio";
    public static final String DATA = "data";
    public static final String FORMAT = "format";
    public static final String IMAGE_URL = "image_url";
    public static final String URL = "url";
    public static final String DETAIL = "detail";

    // Response fields
    public static final String CHOICES = "choices";
    public static final String MESSAGE = "message";
    public static final String DELTA = "delta";
    public static final String FINISH_REASON = "finish_reason";
    public static final String REFUSAL = "refusal";
    public static final String REASONING_CONTENT = "reasoning_content";
    public static final String USAGE = "usage";
    public static final String INDEX = "index";
    public static final String SCHEMA = "schema";
    public static final String JSON_SCHEMA = "json_schema";

    // Usage fields
    public static final String PROMPT_TOKENS = "prompt_tokens";
    public static final String COMPLETION_TOKENS = "completion_tokens";
    public static final String TOTAL_TOKENS = "total_tokens";
    public static final String PROMPT_TOKENS_DETAILS = "prompt_tokens_details";
    public static final String COMPLETION_TOKENS_DETAILS = "completion_tokens_details";
    public static final String AUDIO_TOKENS = "audio_tokens";
    public static final String CACHED_TOKENS = "cached_tokens";
    public static final String REASONING_TOKENS = "reasoning_tokens";

    // Role values
    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_TOOL = "tool";

    // Tool values
    public static final String TYPE_FUNCTION = "function";

    // Tool choice values
    public static final String TOOL_CHOICE_AUTO = "auto";
    public static final String TOOL_CHOICE_REQUIRED = "required";

    // Finish reason values
    public static final String FUNCTION_CALL = "function_call";
}
