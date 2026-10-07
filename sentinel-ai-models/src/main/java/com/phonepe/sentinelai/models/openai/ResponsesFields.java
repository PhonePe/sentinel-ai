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
 * Field-name constants of the OpenAI Responses wire format. The codec and the protocol use only
 * these constants when they build or read JSON, so no string literals appear in the wire code.
 */
@UtilityClass
public class ResponsesFields {

    // Request body fields
    public static final String MODEL = "model";
    public static final String INPUT = "input";
    public static final String INSTRUCTIONS = "instructions";
    public static final String MAX_OUTPUT_TOKENS = "max_output_tokens";
    public static final String TEMPERATURE = "temperature";
    public static final String TOP_P = "top_p";
    public static final String PARALLEL_TOOL_CALLS = "parallel_tool_calls";
    public static final String PREVIOUS_RESPONSE_ID = "previous_response_id";
    public static final String REASONING = "reasoning";
    public static final String STORE = "store";
    public static final String STREAM = "stream";
    public static final String TEXT = "text";
    public static final String TOOL_CHOICE = "tool_choice";
    public static final String TOOLS = "tools";
    public static final String USER = "user";

    // Structured output text format
    public static final String FORMAT = "format";
    public static final String TYPE = "type";
    public static final String JSON_SCHEMA = "json_schema";
    public static final String NAME = "name";
    public static final String SCHEMA = "schema";
    public static final String STRICT = "strict";
    public static final String PROPERTIES = "properties";

    // Tools (flat: {type: function, name, description, parameters, strict})
    public static final String DESCRIPTION = "description";
    public static final String PARAMETERS = "parameters";

    // Input item fields
    public static final String ROLE = "role";
    public static final String CONTENT = "content";
    public static final String ID = "id";
    public static final String CALL_ID = "call_id";
    public static final String ARGUMENTS = "arguments";
    public static final String OUTPUT = "output";

    // Input content part fields
    public static final String TEXT_FIELD = "text";
    public static final String INPUT_TEXT = "input_text";
    public static final String INPUT_IMAGE = "input_image";
    public static final String INPUT_FILE = "input_file";
    public static final String IMAGE_URL = "image_url";
    public static final String DETAIL = "detail";
    public static final String FILE_ID = "file_id";
    public static final String FILE_DATA = "file_data";
    public static final String FILENAME = "filename";

    // Response fields
    public static final String RESPONSE_OUTPUT = "output";
    public static final String OUTPUT_TEXT = "output_text";
    public static final String REFUSAL = "refusal";
    public static final String STATUS = "status";
    public static final String ERROR = "error";
    public static final String MESSAGE = "message";
    public static final String PARAM = "param";
    public static final String CODE = "code";
    public static final String INCOMPLETE_DETAILS = "incomplete_details";
    public static final String REASON = "reason";
    public static final String USAGE = "usage";
    public static final String INPUT_TOKENS = "input_tokens";
    public static final String OUTPUT_TOKENS = "output_tokens";
    public static final String TOTAL_TOKENS = "total_tokens";
    public static final String INPUT_TOKEN_DETAILS = "input_token_details";
    public static final String OUTPUT_TOKEN_DETAILS = "output_token_details";
    public static final String CACHED_TOKENS = "cached_tokens";
    public static final String REASONING_TOKENS = "reasoning_tokens";
    public static final String EFFORT = "effort";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_INCOMPLETE = "incomplete";

    // Role values
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_DEVELOPER = "developer";

    // Tool values
    public static final String TYPE_FUNCTION = "function";

    // Item type values
    public static final String ITEM_MESSAGE = "message";
    public static final String ITEM_FUNCTION_CALL = "function_call";
    public static final String ITEM_FUNCTION_CALL_OUTPUT = "function_call_output";
    public static final String ITEM_REASONING = "reasoning";

    // Tool choice values
    public static final String TOOL_CHOICE_AUTO = "auto";
    public static final String TOOL_CHOICE_REQUIRED = "required";
    public static final String TOOL_CHOICE_NONE = "none";

    // Internal chaining markers (never sent to the provider; the protocol strips them while
    // assembling the request body)
    public static final String MARKER_RESPONSE_ID = "_responseId";
    public static final String MARKER_RUN_ID = "_runId";
    public static final String MARKER_COMPACTED = "_compacted";

    // Stream event names
    public static final String EVENT_RESPONSE_COMPLETED = "response.completed";
    public static final String EVENT_RESPONSE_FAILED = "response.failed";
    public static final String EVENT_RESPONSE_INCOMPLETE = "response.incomplete";
    public static final String EVENT_OUTPUT_ITEM_ADDED = "response.output_item.added";
    public static final String EVENT_OUTPUT_ITEM_DONE = "response.output_item.done";
    public static final String EVENT_OUTPUT_TEXT_DELTA = "response.output_text.delta";
    public static final String EVENT_REASONING_DELTA = "response.reasoning.delta";
    public static final String EVENT_REASONING_SUMMARY_TEXT_DELTA = "response.reasoning_summary_text.delta";
    public static final String EVENT_FUNCTION_CALL_ARGUMENTS_DELTA = "response.function_call_arguments.delta";
    public static final String EVENT_REFUSAL_DELTA = "response.refusal.delta";

    // Stream event payload fields
    public static final String RESPONSE = "response";
    public static final String ITEM = "item";
    public static final String ITEM_ID = "item_id";
    public static final String OUTPUT_INDEX = "output_index";
    public static final String DELTA = "delta";
}
