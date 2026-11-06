package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptFactory;
import com.theminespirit.core.script.CastObsidianParams;
import com.theminespirit.core.script.LightPortalParams;
import java.util.Map;
import java.util.function.Function;

final class PortalRegistrations {
    private PortalRegistrations() {}

    static void register(Map<String, Function<SpiritEntity, ScriptFactory>> table) {
        table.put("cast_obsidian", spirit -> params -> new CastObsidianScript(spirit, CastObsidianParams.from(params)));
        table.put("light_portal", spirit -> params -> new LightPortalScript(spirit, LightPortalParams.from(params)));
    }
}
