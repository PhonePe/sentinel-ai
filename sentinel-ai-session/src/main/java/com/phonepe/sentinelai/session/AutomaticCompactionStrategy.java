package com.phonepe.sentinelai.session;

import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentRunContext;

public class AutomaticCompactionStrategy implements CompactionStrategy {

    @Override
    public boolean isCompactionNeeded(AgentRunContext<?> context, AgentOutput<?> output) {
        // TODO: USE JTOKKIT TO DETERMINE IF IT IS NEEDED OR NOT
        throw new UnsupportedOperationException("Unimplemented method 'isCompactionNeeded'");
    }

    
}
