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
