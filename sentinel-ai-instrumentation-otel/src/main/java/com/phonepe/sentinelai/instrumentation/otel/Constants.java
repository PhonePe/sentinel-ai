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

package com.phonepe.sentinelai.instrumentation.otel;

import io.opentelemetry.api.common.AttributeKey;

/**
 * Constants for OpenTelemetry agent instrumentation.
 */
final class Constants {

    static final String OPERATION_INVOKE_AGENT = "invoke_agent";
    static final String OPERATION_EXECUTE_TOOL = "execute_tool";

    static final AttributeKey<String> ATTR_OPERATION_NAME = AttributeKey.stringKey("gen_ai.operation.name");
    static final AttributeKey<String> ATTR_PROVIDER_NAME = AttributeKey.stringKey("gen_ai.provider.name");
    static final AttributeKey<String> ATTR_AGENT_NAME = AttributeKey.stringKey("gen_ai.agent.name");
    static final AttributeKey<String> ATTR_CONVERSATION_ID = AttributeKey.stringKey("gen_ai.conversation.id");
    static final AttributeKey<Long> ATTR_USAGE_INPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.input_tokens");
    static final AttributeKey<Long> ATTR_USAGE_OUTPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.output_tokens");
    static final AttributeKey<String> ATTR_TOOL_NAME = AttributeKey.stringKey("gen_ai.tool.name");
    static final AttributeKey<String> ATTR_TOOL_CALL_ID = AttributeKey.stringKey("gen_ai.tool.call.id");
    static final AttributeKey<String> ATTR_TOOL_CALL_ARGUMENTS = AttributeKey.stringKey("gen_ai.tool.call.arguments");
    static final AttributeKey<String> ATTR_TOOL_CALL_RESULT = AttributeKey.stringKey("gen_ai.tool.call.result");
    static final AttributeKey<String> ATTR_ERROR_TYPE = AttributeKey.stringKey("error.type");

    static final String TOOL_APPROVAL_DENIED_ERROR = "tool_call_approval_denied";
    static final String TOOL_INCOMPLETE_ERROR = "tool_call_incomplete";
    static final String RUN_INCOMPLETE_ERROR = "run_incomplete";

    private Constants() {
        // Utility class
    }
}
