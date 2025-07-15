package com.phonepe.sentinelai.core.tools;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.utils.ToolUtils;

import java.util.Map;

/**
 *
 */
public interface ToolBox {
    String name();

    default Map<String, ExecutableTool> tools() {
        return ToolUtils.readTools(this);
    }

    default <R, T, A extends Agent<R, T, A>> void onToolBoxRegistrationCompleted(A agent) {
        // Do nothing by default
    }

}
