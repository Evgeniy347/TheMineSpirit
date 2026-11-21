package com.theminespirit.neoforge.movement;

import com.theminespirit.core.movement.api.GazeAhead;
import com.theminespirit.core.movement.api.GazeTurn;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;

public final class WalkGaze {
    private WalkGaze() {}

    public static void tick(Mob mob, boolean working) {
        Path path = mob.getNavigation().getPath();
        if (!GazeAhead.steer(path != null && !path.isDone(), working)) {
            return;
        }
        BlockPos next = path.getNextNodePos();
        float turn = GazeTurn.forWork(false);
        mob.getLookControl().setLookAt(next.getX() + 0.5, mob.getEyeY(), next.getZ() + 0.5, turn, turn);
    }
}
