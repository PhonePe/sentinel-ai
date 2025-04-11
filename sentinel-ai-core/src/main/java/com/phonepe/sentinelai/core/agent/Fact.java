package com.phonepe.sentinelai.core.agent;

import lombok.Value;

/**
 * A piece of information to be passed to the agent.
 */
@Value
public class Fact {
    /**
     * A meaningful name for the fact. This is used to identify the fact in the system prompt.
     */
    String name;
    /**
     * The actual content of the fact. This is used to provide additional information to the agent.
     */
    String content;
}
