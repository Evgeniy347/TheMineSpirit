package com.theminespirit.neoforge;

import com.theminespirit.core.script.DigAreaParams;
import com.theminespirit.core.script.DigDescentParams;
import com.theminespirit.core.script.MineVeinParams;
import com.theminespirit.core.script.PillarUpParams;
import com.theminespirit.core.script.SafeMineParams;

final class MiningScriptPack extends SpiritScriptPack {
    @Override
    public String id() {
        return "mining";
    }

    @Override
    protected void register() {
        put("dig_out", spirit -> DigOutScript.factory(spirit));
        put("pillar_up", spirit -> params -> new PillarUpScript(spirit, PillarUpParams.from(params)));
        put("pillar_down", spirit -> params -> new PillarDownScript(spirit));
        put("dig_area", spirit -> params -> new DigAreaScript(spirit, DigAreaParams.from(params)));
        put("dig_descent", spirit -> params -> new DescentScript(spirit, DigDescentParams.from(params)));
        put("safe_mine", spirit -> params -> new SafeMineScript(spirit, SafeMineParams.from(params)));
        put("dig_descent_widen", spirit -> params -> new DigDescentWidenScript(spirit, DigDescentParams.from(params)));
        put("mine_vein", spirit -> params -> new MineVeinScript(spirit, MineVeinParams.from(params)));
    }
}
