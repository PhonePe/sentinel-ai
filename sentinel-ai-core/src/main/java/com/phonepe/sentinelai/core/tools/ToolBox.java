package com.phonepe.sentinelai.core.tools;

import com.phonepe.sentinelai.core.utils.ToolReader;

import java.util.Map;

/**
 *
 */
public interface ToolBox {
    default Map<String, ExecutableTool> tools() {
        return ToolReader.readTools(this);
    }
}
