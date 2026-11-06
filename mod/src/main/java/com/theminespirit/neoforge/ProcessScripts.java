package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptFactory;
import com.theminespirit.core.script.BrewParams;
import com.theminespirit.core.script.SmeltParams;
import com.theminespirit.core.script.WartFarmParams;
import com.theminespirit.neoforge.crafting.BrewScript;
import com.theminespirit.neoforge.crafting.SmeltScript;
import java.util.Map;
import java.util.function.Function;

final class ProcessScripts {
    private ProcessScripts() {}

    static void register(Map<String, Function<SpiritEntity, ScriptFactory>> table) {
        table.put("smelt", spirit -> params -> new SmeltScript(spirit, SmeltParams.from(params)));
        table.put("brew", spirit -> params -> new BrewScript(spirit, BrewParams.from(params)));
        table.put(
                "farm_nether_wart", spirit -> params -> new FarmNetherWartScript(spirit, WartFarmParams.from(params)));
        BucketRegistrations.register(table);
    }
}
