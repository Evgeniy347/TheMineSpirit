package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.movement.GotoTuning;
import com.theminespirit.core.movement.PathRefreshPolicy;
import com.theminespirit.core.movement.StuckWatcher;
import com.theminespirit.core.script.GotoParams;
import net.minecraft.world.phys.Vec3;

final class GotoScript implements ScriptInstance {
    private static final double ARRIVAL_TOLERANCE = 1.25;
    private static final double MOVE_SPEED = 1.0;

    private final SpiritEntity spirit;
    private final Vec3 target;
    private final PathRefreshPolicy pathRefresh = new PathRefreshPolicy();
    private final StuckWatcher stuckWatcher =
            new StuckWatcher(GotoTuning.STUCK_MIN_MOVEMENT, GotoTuning.STUCK_TICKS);
    private final int timeoutTicks;
    private String failureReason;
    private int elapsed;

    GotoScript(SpiritEntity spirit, GotoParams params) {
        this.spirit = spirit;
        this.target = new Vec3(params.x() + 0.5, params.y(), params.z() + 0.5);
        this.timeoutTicks = GotoTuning.timeoutTicks(spirit.position().distanceTo(this.target));
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
        if (elapsed++ >= timeoutTicks) {
            stopNavigation();
            failureReason = failedToArriveReason();
            return ScriptStatus.FAILED;
        }
        if (stuckWatcher.observe(spirit.getX(), spirit.getY(), spirit.getZ())) {
            stopNavigation();
            failureReason = stuckReason();
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

    private String failedToArriveReason() {
        return "не дошёл до (x=" + target.x + ", y=" + target.y + ", z=" + target.z + ") за "
                + timeoutTicks + " тиков";
    }

    private String stuckReason() {
        return "застрял в (x=" + spirit.getX() + ", y=" + spirit.getY() + ", z=" + spirit.getZ() + ")";
    }
}