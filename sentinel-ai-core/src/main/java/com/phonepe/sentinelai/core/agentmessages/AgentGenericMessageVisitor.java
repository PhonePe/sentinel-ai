package com.phonepe.sentinelai.core.agentmessages;

import com.phonepe.sentinelai.core.agentmessages.requests.GenericResource;
import com.phonepe.sentinelai.core.agentmessages.requests.GenericText;

/**
 *
 */
public interface AgentGenericMessageVisitor<T> {

    T visit(GenericText genericText);

    T visit(GenericResource genericResource);
}
