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

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.utils.ToolUtils;

import java.util.Map;

/**
 *
 */
public interface ToolBox {
    String name();

    default <R, T, A extends Agent<R, T, A>> void onToolBoxRegistrationCompleted(A agent) {
        // Do nothing by default
    }

    default Map<String, ExecutableTool> tools() {
        return ToolUtils.readTools(this);
    }

}
