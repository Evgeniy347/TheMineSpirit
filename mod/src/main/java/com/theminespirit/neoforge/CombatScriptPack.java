package com.theminespirit.neoforge;

import com.theminespirit.core.script.AttackParams;
import com.theminespirit.core.script.DefendParams;
import com.theminespirit.core.script.DisengageParams;
import com.theminespirit.core.script.FleeParams;
import com.theminespirit.core.script.RetreatParams;
import com.theminespirit.core.script.SetCombatModeParams;
import com.theminespirit.neoforge.combat.AttackScript;
import com.theminespirit.neoforge.combat.DefendScript;
import com.theminespirit.neoforge.combat.DisengageScript;
import com.theminespirit.neoforge.combat.FleeScript;
import com.theminespirit.neoforge.combat.RetreatScript;
import com.theminespirit.neoforge.combat.SetCombatModeScript;

final class CombatScriptPack extends SpiritScriptPack {
    @Override
    public String id() {
        return "combat";
    }

    @Override
    protected void register() {
        put("attack", spirit -> observing(params -> new AttackScript(spirit, AttackParams.from(params))));
        put("defend", spirit -> observing(params -> new DefendScript(spirit, DefendParams.from(params))));
        put("flee", spirit -> observing(params -> new FleeScript(spirit, FleeParams.from(params))));
        put("retreat", spirit -> observing(params -> new RetreatScript(spirit, RetreatParams.from(params))));
        put("disengage", spirit -> observing(params -> new DisengageScript(spirit, DisengageParams.from(params))));
        put("set_combat_mode", spirit -> params -> new SetCombatModeScript(spirit, SetCombatModeParams.from(params)));
    }
}
