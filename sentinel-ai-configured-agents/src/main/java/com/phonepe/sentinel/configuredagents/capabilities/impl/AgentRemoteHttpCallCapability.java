package com.phonepe.sentinel.configuredagents.capabilities.impl;

import com.phonepe.sentinel.configuredagents.ConfiguredAgent;
import com.phonepe.sentinel.configuredagents.capabilities.AgentCapability;
import com.phonepe.sentinel.configuredagents.capabilities.AgentCapabilityVisitor;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;
import java.util.Set;

/**
 * Provides the capability for a {@link ConfiguredAgent} to make remote HTTP calls.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AgentRemoteHttpCallCapability extends AgentCapability {
    /**
     * A map of upstreams, vs the set of tools selected form that upstream. To select all available tools, the Set
     * can be left as empty.
     */
    Map<String, Set<String>> selectedTools;

    @Builder
    @Jacksonized
    public AgentRemoteHttpCallCapability(@NonNull Map<String, Set<String>> selectedTools) {
        super(Type.REMOTE_HTTP_CALLS);
        this.selectedTools = selectedTools;
    }

    @Override
    public <T> T accept(AgentCapabilityVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
