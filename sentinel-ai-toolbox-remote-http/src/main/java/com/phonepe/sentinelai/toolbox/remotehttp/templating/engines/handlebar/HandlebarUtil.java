package com.phonepe.sentinelai.toolbox.remotehttp.templating.engines.handlebar;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import lombok.Getter;
import lombok.experimental.UtilityClass;

@UtilityClass
public class HandlebarUtil {

    @Getter
    private static final Handlebars handlebars;

    static {
        handlebars = new Handlebars();
    }

    public <H> void registerHelper(String name, Helper<H> helper) {
        handlebars.registerHelper(name, helper);
    }
}
