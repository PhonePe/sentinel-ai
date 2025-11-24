package com.phonepe.sentinelai.configuredagents.capabilities.impl;

import com.google.common.base.Preconditions;
import com.phonepe.sentinelai.configuredagents.capabilities.AgentCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.AgentCapabilityVisitor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Set;

/**
 * Inherits all or the provided set of tools from the parent agent.
 * If capability is provided and selected tools not specified, all parent tools are inherited.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ParentToolInheritanceCapability extends AgentCapability {

    Set<String> selectedTools;

    @Builder
    @Jacksonized
    public ParentToolInheritanceCapability(Set<String> selectedTools) {
        super(Type.TOOL_INHERITANCE);
        Preconditions.checkArgument(null != selectedTools && !selectedTools.isEmpty(),
                "Specific tools must be provided for tool inheritance capability");
        this.selectedTools = selectedTools;
    }

    @Override
    public <T> T accept(AgentCapabilityVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
