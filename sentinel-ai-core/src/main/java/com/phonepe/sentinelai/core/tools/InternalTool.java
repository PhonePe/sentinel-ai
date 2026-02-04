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

package com.phonepe.sentinelai.core.tools;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class InternalTool extends ExecutableTool {

    ToolMethodInfo methodInfo;
    Object instance;

    public InternalTool(
            ToolDefinition toolDefinition,
            ToolMethodInfo toolMethodInfo,
            Object instance) {
        super(toolDefinition);
        this.instance = instance;
        this.methodInfo = toolMethodInfo;
    }

    @Override
    public <T> T accept(ExecutableToolVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
