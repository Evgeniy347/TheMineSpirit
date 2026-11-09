package com.theminespirit.neoforge;

import com.theminespirit.core.script.CraftParams;
import com.theminespirit.neoforge.crafting.CraftScript;

final class CraftingScriptPack extends SpiritScriptPack {
    @Override
    public String id() {
        return "crafting";
    }

    @Override
    protected void register() {
        put("craft", spirit -> params -> new CraftScript(spirit, CraftParams.from(params)));
        ProcessScripts.register(this);
    }
}
