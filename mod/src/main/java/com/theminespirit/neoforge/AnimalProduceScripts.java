package com.theminespirit.neoforge;

import com.theminespirit.core.script.BrushParams;
import com.theminespirit.core.script.CollectEggsParams;
import com.theminespirit.core.script.HarvestHiveParams;
import com.theminespirit.core.script.MilkParams;
import com.theminespirit.core.script.ShearParams;
import com.theminespirit.neoforge.husbandry.CollectEggsScript;
import com.theminespirit.neoforge.husbandry.HarvestHiveScript;
import com.theminespirit.neoforge.husbandry.MilkScript;
import com.theminespirit.neoforge.husbandry.ShearScript;

final class AnimalProduceScripts {
    private AnimalProduceScripts() {}

    static void register(SpiritScriptPack pack) {
        pack.put("shear", spirit -> params -> new ShearScript(spirit, ShearParams.from(params)));
        pack.put("milk", spirit -> params -> new MilkScript(spirit, MilkParams.from(params)));
        pack.put("collect_eggs", spirit -> params -> new CollectEggsScript(spirit, CollectEggsParams.from(params)));
        pack.put("harvest_hive", spirit -> params -> new HarvestHiveScript(spirit, HarvestHiveParams.from(params)));
        pack.put("brush", spirit -> params -> new BrushScript(spirit, BrushParams.from(params)));
    }
}
