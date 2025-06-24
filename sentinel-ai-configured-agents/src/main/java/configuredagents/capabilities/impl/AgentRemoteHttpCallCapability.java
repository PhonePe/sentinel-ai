package configuredagents.capabilities.impl;

import configuredagents.ConfiguredAgent;
import configuredagents.capabilities.AgentCapability;
import configuredagents.capabilities.AgentCapabilityVisitor;
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
    Map<String, Set<String>> selectedRemoteTools;

    @Builder
    @Jacksonized
    public AgentRemoteHttpCallCapability(@NonNull Map<String, Set<String>> selectedRemoteTools) {
        super(Type.REMOTE_HTTP_CALLS);
        this.selectedRemoteTools = selectedRemoteTools;
    }

    @Override
    public <T> T accept(AgentCapabilityVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
