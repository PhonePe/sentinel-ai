package com.phonepe.sentinelai.core.tools;

import com.phonepe.sentinelai.core.utils.ToolUtils;

import java.util.Map;

/**
 *
 */
public interface ToolBox {
    default Map<String, ExecutableTool> tools() {
        return ToolUtils.readTools(this);
    }
}
