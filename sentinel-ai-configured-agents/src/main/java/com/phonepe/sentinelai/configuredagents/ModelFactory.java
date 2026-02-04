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

package com.phonepe.sentinelai.configuredagents;

import com.phonepe.sentinelai.core.model.Model;

/**
 * A generic interface to dynamically create models on the fly.
 * The model from the parent agent setup will be passed as the defaultModel, implementation can decide to return it or
 * throw an exception based on the circumstances.
 */
@FunctionalInterface
public interface ModelFactory {
    Model build(AgentConfiguration agentConfig, final Model defaultModel);
}
