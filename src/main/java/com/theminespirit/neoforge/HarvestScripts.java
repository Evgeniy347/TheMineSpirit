package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptFactory;
import com.theminespirit.core.script.ChopTargetParams;
import com.theminespirit.core.script.ChopTreeParams;
import com.theminespirit.core.script.StripLogParams;
import java.util.Map;
import java.util.function.Function;

final class HarvestScripts {
    private HarvestScripts() {}

    static void register(Map<String, Function<SpiritEntity, ScriptFactory>> table) {
        table.put("chop_tree", spirit -> params -> new ChopTreeScript(spirit, ChopTreeParams.from(params)));
        table.put("chop_target", spirit -> params -> new ChopTargetScript(spirit, ChopTargetParams.from(params)));
        table.put("strip_log", spirit -> params -> new StripLogScript(spirit, StripLogParams.from(params)));
    }
}
