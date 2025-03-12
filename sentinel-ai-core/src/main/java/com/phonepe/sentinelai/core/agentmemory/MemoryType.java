package com.phonepe.sentinelai.core.agentmemory;

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

