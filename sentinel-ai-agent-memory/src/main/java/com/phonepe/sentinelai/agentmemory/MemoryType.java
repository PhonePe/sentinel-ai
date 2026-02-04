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
 * Types of memory for agents
 */
public enum MemoryType {
    /**
     * AgentMemory for storing facts about a subject. For example name of a user etc
     */
    SEMANTIC,
    /**
     * AgentMemory for storing procedural information, as in how to achieve a certain task
     */
    PROCEDURAL,
    /**
     * AgentMemory for storing episodic information, as in what happened in a certain event
     */
    EPISODIC,
}

