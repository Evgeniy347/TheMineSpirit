package com.theminespirit.neoforge;

import com.theminespirit.core.script.BlockPointParams;
import com.theminespirit.core.script.DeliverParams;
import com.theminespirit.core.script.DrinkPotionParams;
import com.theminespirit.core.script.DropToPlayerParams;
import com.theminespirit.core.script.EatParams;
import com.theminespirit.core.script.RepairParams;
import com.theminespirit.core.script.ReportParams;
import com.theminespirit.core.script.SayParams;
import com.theminespirit.core.script.TakeParams;
import com.theminespirit.core.script.WaitParams;
import com.theminespirit.neoforge.doors.CloseDoorScript;
import com.theminespirit.neoforge.hunger.DrinkPotionScript;
import com.theminespirit.neoforge.hunger.EatScript;
import com.theminespirit.neoforge.repair.RepairScript;
import com.theminespirit.neoforge.sleep.SleepScript;
import com.theminespirit.neoforge.social.DeliverScript;
import com.theminespirit.neoforge.social.DropToPlayerScript;
import com.theminespirit.neoforge.social.ReportScript;
import com.theminespirit.neoforge.social.SayScript;
import com.theminespirit.neoforge.social.TakeScript;
import com.theminespirit.neoforge.social.WaitScript;

final class SmallMechanicsScriptPack extends SpiritScriptPack {
    @Override
    public String id() {
        return "small-mechanics";
    }

    @Override
    protected void register() {
        put("deliver", spirit -> params -> new DeliverScript(spirit, DeliverParams.from(params)));
        put("drop_to_player", spirit -> params -> new DropToPlayerScript(spirit, DropToPlayerParams.from(params)));
        put("give", spirit -> params -> new DeliverScript(spirit, DeliverParams.from(params)));
        put("take", spirit -> params -> new TakeScript(spirit, TakeParams.from(params)));
        put("report", spirit -> params -> new ReportScript(spirit, ReportParams.from(params)));
        put("wait", spirit -> params -> new WaitScript(spirit, WaitParams.from(params)));
        put("say", spirit -> params -> new SayScript(spirit, SayParams.from(params)));
        put("sleep", spirit -> observing(params -> new SleepScript(spirit)));
        put("drink_potion", spirit -> params -> new DrinkPotionScript(spirit, DrinkPotionParams.from(params)));
        put("eat", spirit -> params -> new EatScript(spirit, EatParams.from(params)));
        put("repair_tool", spirit -> params -> new RepairScript(spirit, RepairParams.from(params)));
        put("close_door", spirit -> params -> new CloseDoorScript(spirit, BlockPointParams.from(params)));
    }
}
