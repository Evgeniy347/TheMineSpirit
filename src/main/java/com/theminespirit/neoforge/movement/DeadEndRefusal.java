package com.theminespirit.neoforge.movement;

import com.theminespirit.core.movement.GotoTuning;
import com.theminespirit.neoforge.PathMode;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class DeadEndRefusal {
    private DeadEndRefusal() {}

    public static @Nullable String refusal(
            SpiritEntity spirit, Vec3 target, boolean entityTarget, PathMode mode, double arrival) {
        if (entityTarget || mode == PathMode.CAREFUL || mode == PathMode.SWIM) {
            return null;
        }
        if (spirit.position().distanceTo(target) > GotoTuning.EARLY_REFUSAL_RANGE) {
            return null;
        }
        Path path = spirit.getNavigation().getPath();
        if (path == null || !spirit.getNavigation().isDone()) {
            return null;
        }
        Vec3 end = new Vec3(path.getEndNode().x + 0.5, path.getEndNode().y, path.getEndNode().z + 0.5);
        if (end.distanceTo(target) <= arrival) {
            return null;
        }
        if (spirit.position().distanceTo(end) > arrival) {
            return null;
        }
        return "путь упёрся в тупик в пределах " + GotoTuning.EARLY_REFUSAL_RANGE
                + " блоков от цели: стоянка за преградой";
    }
}
