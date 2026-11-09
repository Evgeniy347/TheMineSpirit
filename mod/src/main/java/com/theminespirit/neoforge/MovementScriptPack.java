package com.theminespirit.neoforge;

import com.theminespirit.core.script.ApproachParams;
import com.theminespirit.core.script.BridgeParams;
import com.theminespirit.core.script.ClimbParams;
import com.theminespirit.core.script.ComeToPlayerParams;
import com.theminespirit.core.script.CrossWaterParams;
import com.theminespirit.core.script.EscortParams;
import com.theminespirit.core.script.ExploreParams;
import com.theminespirit.core.script.FollowParams;
import com.theminespirit.core.script.GotoBlockParams;
import com.theminespirit.core.script.GotoParams;
import com.theminespirit.core.script.KeepDistanceParams;
import com.theminespirit.core.script.MeetParams;
import com.theminespirit.core.script.PatrolParams;
import com.theminespirit.core.script.StepOverParams;
import com.theminespirit.core.script.SwimParams;
import com.theminespirit.core.script.WalkCarefulParams;
import com.theminespirit.core.script.WanderParams;
import com.theminespirit.neoforge.movement.ApproachScript;
import com.theminespirit.neoforge.movement.BridgeScript;
import com.theminespirit.neoforge.movement.ClimbScript;
import com.theminespirit.neoforge.movement.ComeToPlayerScript;
import com.theminespirit.neoforge.movement.CrossWaterScript;
import com.theminespirit.neoforge.movement.EscortScript;
import com.theminespirit.neoforge.movement.ExploreScript;
import com.theminespirit.neoforge.movement.GotoBlockScript;
import com.theminespirit.neoforge.movement.KeepDistanceScript;
import com.theminespirit.neoforge.movement.MeetScript;
import com.theminespirit.neoforge.movement.PatrolScript;
import com.theminespirit.neoforge.movement.ReturnHomeScript;
import com.theminespirit.neoforge.movement.StepOverScript;
import com.theminespirit.neoforge.movement.SwimScript;
import com.theminespirit.neoforge.movement.WanderScript;

final class MovementScriptPack extends SpiritScriptPack {
    @Override
    public String id() {
        return "movement";
    }

    @Override
    protected void register() {
        put("goto", spirit -> params -> new GotoScript(spirit, GotoParams.from(params)));
        put("follow", spirit -> params -> new FollowScript(spirit, FollowParams.from(params)));
        put("escort", spirit -> params -> new EscortScript(spirit, EscortParams.from(params)));
        put("return_home", spirit -> params -> new ReturnHomeScript(spirit));
        put("goto_block", spirit -> params -> new GotoBlockScript(spirit, GotoBlockParams.from(params)));
        put("come_to_player", spirit -> params -> new ComeToPlayerScript(spirit, ComeToPlayerParams.from(params)));
        put("patrol", spirit -> params -> new PatrolScript(spirit, PatrolParams.from(params)));
        put("wander", spirit -> params -> new WanderScript(spirit, WanderParams.from(params)));
        put("explore", spirit -> params -> new ExploreScript(spirit, ExploreParams.from(params)));
        put("keep_distance", spirit -> params -> new KeepDistanceScript(spirit, KeepDistanceParams.from(params)));
        put("walk_careful", spirit -> params -> GotoScript.careful(spirit, WalkCarefulParams.from(params)));
        put("step_over", spirit -> params -> new StepOverScript(spirit, StepOverParams.from(params)));
        put("climb", spirit -> params -> new ClimbScript(spirit, ClimbParams.from(params)));
        put("approach", spirit -> params -> new ApproachScript(spirit, ApproachParams.from(params)));
        put("swim", spirit -> params -> new SwimScript(spirit, SwimParams.from(params)));
        put("cross_water", spirit -> params -> new CrossWaterScript(spirit, CrossWaterParams.from(params)));
        put("bridge", spirit -> params -> new BridgeScript(spirit, BridgeParams.from(params)));
        put("meet", spirit -> params -> new MeetScript(spirit, MeetParams.from(params)));
    }
}
