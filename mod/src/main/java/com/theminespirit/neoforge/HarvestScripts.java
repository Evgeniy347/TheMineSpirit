package com.theminespirit.neoforge;

import com.theminespirit.core.script.ChopTargetParams;
import com.theminespirit.core.script.ChopTreeParams;
import com.theminespirit.core.script.StripLogParams;

final class HarvestScripts {
    private HarvestScripts() {}

    static void register(SpiritScriptPack pack) {
        pack.put("chop_tree", spirit -> params -> new ChopTreeScript(spirit, ChopTreeParams.from(params)));
        pack.put("chop_target", spirit -> params -> new ChopTargetScript(spirit, ChopTargetParams.from(params)));
        pack.put("strip_log", spirit -> params -> new StripLogScript(spirit, StripLogParams.from(params)));
    }
}
