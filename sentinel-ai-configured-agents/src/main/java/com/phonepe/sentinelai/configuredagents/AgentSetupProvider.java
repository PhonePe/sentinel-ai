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

import com.phonepe.sentinelai.core.agent.AgentSetup;

/**
 * Provides a fresh AgentSetup based on the configuration provided and the model factory. Uses the source config
 * to fill in any blanks not provided in the agent configuration.
 */
@FunctionalInterface
public interface AgentSetupProvider {
    AgentSetup from(final AgentSetup source, final AgentConfiguration agentConfiguration, ModelFactory modelFactory);
}
