package com.theminespirit.neoforge;

import com.theminespirit.core.script.BrewParams;
import com.theminespirit.core.script.SmeltParams;
import com.theminespirit.core.script.WartFarmParams;
import com.theminespirit.neoforge.crafting.BrewScript;
import com.theminespirit.neoforge.crafting.SmeltScript;

final class ProcessScripts {
    private ProcessScripts() {}

    static void register(SpiritScriptPack pack) {
        pack.put("smelt", spirit -> params -> new SmeltScript(spirit, SmeltParams.from(params)));
        pack.put("brew", spirit -> params -> new BrewScript(spirit, BrewParams.from(params)));
        pack.put("farm_nether_wart", spirit -> params -> new FarmNetherWartScript(spirit, WartFarmParams.from(params)));
        BucketRegistrations.register(pack);
    }
}
