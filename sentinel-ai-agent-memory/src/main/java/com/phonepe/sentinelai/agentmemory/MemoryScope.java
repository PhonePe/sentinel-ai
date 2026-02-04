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

package com.phonepe.sentinelai.agentmemory;

/**
 * Scope of memory extracted by the agent.
 */
public enum MemoryScope {
    /**
     * Memory that is relevant to the agent's own actions and decisions. For example, if the agent is used to query an
     * analytics store, a relevant agent level memory would be the interpretation of a particular field in the db.
     */
    AGENT,
    /**
     * Memory that is relevant to the entity being interacted with by the agent. For example, if the agent is a customer
     * service agent, this would be the memory relevant to the customer.
     */
    ENTITY,
}
