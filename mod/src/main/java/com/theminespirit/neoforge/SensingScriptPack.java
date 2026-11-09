package com.theminespirit.neoforge;

import com.theminespirit.core.script.ClearCampParams;
import com.theminespirit.core.script.CollectDropsParams;
import com.theminespirit.core.script.LocateParams;
import com.theminespirit.core.script.LookAtParams;
import com.theminespirit.core.script.MemoryParams;
import com.theminespirit.core.script.PerceptionParams;
import com.theminespirit.core.script.ScanParams;
import com.theminespirit.core.script.SetCampParams;

final class SensingScriptPack extends SpiritScriptPack {
    @Override
    public String id() {
        return "sensing";
    }

    @Override
    protected void register() {
        put("scan", spirit -> params -> new ScanScript(spirit, ScanParams.from(params)));
        put("scan_blocks", spirit -> params -> new ScanScript(spirit, PerceptionParams.blocks(params)));
        put("scan_entities", spirit -> params -> new ScanScript(spirit, PerceptionParams.entities(params)));
        put("sense_hazard", spirit -> params -> new HazardScript(spirit, PerceptionParams.hazards(params)));
        put("locate_block", spirit -> params -> new LocateScript(spirit, LocateParams.block(params)));
        put("locate_tree", spirit -> params -> new LocateScript(spirit, LocateParams.tree(params)));
        put("locate_container", spirit -> params -> new LocateScript(spirit, LocateParams.container(params)));
        put("look_at", spirit -> params -> new LookAtScript(spirit, LookAtParams.target(params)));
        put("remember", spirit -> params -> new MemoryScript(spirit, MemoryParams.remember(params)));
        put("recall", spirit -> params -> new MemoryScript(spirit, MemoryParams.recall(params)));
        put("recall_all", spirit -> params -> new MemoryScript(spirit, MemoryParams.recallAll(params)));
        put("forget", spirit -> params -> new MemoryScript(spirit, MemoryParams.forget(params)));
        put("collect_drops", spirit -> params -> new CollectDropsScript(spirit, CollectDropsParams.from(params)));
        put("set_camp", spirit -> params -> new SetCampScript(spirit, SetCampParams.from(params)));
        put("clear_camp", spirit -> params -> new ClearCampScript(spirit, ClearCampParams.from(params)));
    }
}
