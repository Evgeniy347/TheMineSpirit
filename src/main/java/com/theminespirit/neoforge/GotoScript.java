package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.movement.PathRefreshPolicy;
import com.theminespirit.core.script.GotoParams;
import net.minecraft.world.phys.Vec3;

final class GotoScript implements ScriptInstance {
    private static final double ARRIVAL_TOLERANCE = 1.25;
    private static final double MOVE_SPEED = 1.0;
    private static final int MAX_TICKS = 300;

    private final SpiritEntity spirit;
    private final Vec3 target;
    private final PathRefreshPolicy pathRefresh = new PathRefreshPolicy();
    private String failureReason;
    private int elapsed;

    GotoScript(SpiritEntity spirit, GotoParams params) {
        this.spirit = spirit;
        this.target = new Vec3(params.x() + 0.5, params.y(), params.z() + 0.5);
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            stopNavigation();
            failureReason = "дух исчез до достижения цели";
            return ScriptStatus.FAILED;
        }
        if (spirit.position().distanceTo(target) <= ARRIVAL_TOLERANCE) {
            stopNavigation();
            return ScriptStatus.SUCCEEDED;
        }
        if (elapsed++ >= MAX_TICKS) {
            stopNavigation();
            failureReason = "не дошёл до цели за " + MAX_TICKS + " тиков";
            return ScriptStatus.FAILED;
        }
        boolean pathFinished = spirit.getNavigation().isDone();
        if (pathRefresh.shouldRefresh(target.x, target.y, target.z, pathFinished)) {
            spirit.getNavigation().moveTo(target.x, target.y, target.z, MOVE_SPEED);
            spirit.countPathBuild();
        }
        return ScriptStatus.RUNNING;
    }

    @Override
    public void cancel() {
        stopNavigation();
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    private void stopNavigation() {
        spirit.getNavigation().stop();
    }
}
