package com.phonepe.sentinelai.toolbox.remotehttp.templating;

import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolSource;

/**
 * A source of {@link TemplatizedHttpTool} instances that can be used to create HTTP tools
 */
public interface TemplatizedHttpToolSource<S extends HttpToolSource<TemplatizedHttpTool, S>> extends HttpToolSource<TemplatizedHttpTool, S> {
}
