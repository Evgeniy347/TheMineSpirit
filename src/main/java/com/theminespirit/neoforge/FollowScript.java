package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.movement.PathRefreshPolicy;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.script.FollowParams;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

final class FollowScript implements ScriptInstance {
    private static final double MOVE_SPEED = 1.0;

    private final SpiritEntity spirit;
    private final FollowParams params;
    private final PathRefreshPolicy pathRefresh = new PathRefreshPolicy();
    private String failureReason;

    FollowScript(SpiritEntity spirit, FollowParams params) {
        this.spirit = spirit;
        this.params = params;
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            stopNavigation();
            failureReason = "дух исчез во время сопровождения";
            return ScriptStatus.FAILED;
        }
        ServerPlayer target = findTarget();
        if (target == null) {
            stopNavigation();
            failureReason = "цель '" + params.target() + "' не найдена";
            return ScriptStatus.FAILED;
        }
        BlockPos targetCell = target.blockPosition();
        if (spirit.protectionGate().checkTravel(new BlockCoord(targetCell.getX(), targetCell.getY(), targetCell.getZ()))
                instanceof BlockProtection.Refused refused) {
            stopNavigation();
            failureReason = "цель вышла за границы рабочей зоны: " + refused.reason();
            return ScriptStatus.FAILED;
        }
        if (spirit.position().distanceTo(target.position()) <= params.radius()) {
            return ScriptStatus.RUNNING;
        }
        Vec3 targetPos = target.position();
        boolean pathFinished = spirit.getNavigation().isDone();
        if (pathRefresh.shouldRefresh(targetPos.x, targetPos.y, targetPos.z, pathFinished)) {
            spirit.getNavigation().moveTo(target, MOVE_SPEED);
            spirit.countPathBuild();
        }
        return ScriptStatus.RUNNING;
    }

    @Override
    public void cancel() {
        stopNavigation();
    }

    @Override
    public void suspend() {
        stopNavigation();
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    private void stopNavigation() {
        spirit.getNavigation().stop();
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
