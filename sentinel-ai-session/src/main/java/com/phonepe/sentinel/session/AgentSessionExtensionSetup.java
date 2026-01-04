package com.phonepe.sentinel.session;

import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Value
@Builder
public class AgentSessionExtensionSetup {

    @Singular
    Set<AgentSessionExtensionMode> modes;

}
