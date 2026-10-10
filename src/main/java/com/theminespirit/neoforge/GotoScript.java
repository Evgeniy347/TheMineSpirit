package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.GotoParams;
import net.minecraft.world.phys.Vec3;

final class GotoScript implements ScriptInstance {
    private static final double ARRIVAL_TOLERANCE = 1.25;
    private static final double MOVE_SPEED = 1.0;
    private static final int MAX_TICKS = 300;

    private final SpiritEntity spirit;
    private final Vec3 target;
    private int elapsed;

    GotoScript(SpiritEntity spirit, GotoParams params) {
        this.spirit = spirit;
        this.target = new Vec3(params.x() + 0.5, params.y(), params.z() + 0.5);
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            return ScriptStatus.FAILED;
        }
        if (spirit.position().distanceTo(target) <= ARRIVAL_TOLERANCE) {
            return ScriptStatus.SUCCEEDED;
        }
        if (elapsed++ >= MAX_TICKS) {
            return ScriptStatus.FAILED;
        }
        spirit.getNavigation().moveTo(target.x, target.y, target.z, MOVE_SPEED);
        return ScriptStatus.RUNNING;
    }
}