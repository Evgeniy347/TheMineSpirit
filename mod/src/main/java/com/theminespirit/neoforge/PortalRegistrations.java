package com.theminespirit.neoforge;

import com.theminespirit.core.script.CastObsidianParams;
import com.theminespirit.core.script.LightPortalParams;

final class PortalRegistrations {
    private PortalRegistrations() {}

    static void register(SpiritScriptPack pack) {
        pack.put("cast_obsidian", spirit -> params -> new CastObsidianScript(spirit, CastObsidianParams.from(params)));
        pack.put("light_portal", spirit -> params -> new LightPortalScript(spirit, LightPortalParams.from(params)));
    }
}
