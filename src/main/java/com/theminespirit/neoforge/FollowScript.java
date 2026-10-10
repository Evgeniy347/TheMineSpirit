package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.FollowParams;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

final class FollowScript implements ScriptInstance {
    private static final double MOVE_SPEED = 1.0;

    private final SpiritEntity spirit;
    private final FollowParams params;

    FollowScript(SpiritEntity spirit, FollowParams params) {
        this.spirit = spirit;
        this.params = params;
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            return ScriptStatus.FAILED;
        }
        ServerPlayer target = findTarget();
        if (target == null) {
            return ScriptStatus.FAILED;
        }
        if (spirit.position().distanceTo(target.position()) > params.radius()) {
            spirit.getNavigation().moveTo(target, MOVE_SPEED);
        }
        return ScriptStatus.RUNNING;
    }

    private ServerPlayer findTarget() {
        if (!(spirit.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        for (ServerPlayer player : serverLevel.players()) {
            if (player.getName().getString().equals(params.target())) {
                return player;
            }
        }
        return null;
    }
}