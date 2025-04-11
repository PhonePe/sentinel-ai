package com.phonepe.sentinelai.core.agent;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import lombok.Value;

import java.util.List;

/**
 * A list of facts to be passed to the agent.
 */
@Value
public class FactList {
    /**
     * A meaningful description of the facts being represented in the list of facts.
     * For example, "List of facts about the book"
     */
    String description;

    /**
     * A list of facts to be passed to the agent.
     */
    @JacksonXmlElementWrapper(useWrapping = false)
    List<Fact> fact;
}
