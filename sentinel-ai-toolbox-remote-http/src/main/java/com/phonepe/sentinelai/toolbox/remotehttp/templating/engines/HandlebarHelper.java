package com.phonepe.sentinelai.toolbox.remotehttp.templating.engines;

import com.github.jknack.handlebars.Helper;

public class HandlebarHelper {
    public static <H> void registerHelper(String name, Helper<H> helper) {
        HandlebarHttpCallTemplatingEngine.getHandlebars().registerHelper(name, helper);
    }
}
