package com.phonepe.sentinelai.core.tools;

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
}
