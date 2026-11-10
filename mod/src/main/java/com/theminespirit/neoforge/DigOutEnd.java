package com.theminespirit.neoforge;

import com.theminespirit.core.movement.DigOutGoal;
import com.theminespirit.core.observation.BlockCoord;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

final class DigOutEnd {
    private DigOutEnd() {}

    static boolean reached(Level level, BlockPos feet, BlockCoord target, boolean descending) {
        if (descending) {
            return feet.getY() <= target.y() && (DigOutSpace.open(level, feet) || feet.getY() == target.y());
        }
        return DigOutGoal.ascent(new BlockCoord(feet.getX(), feet.getY(), feet.getZ()), target, DigOutGoal.HOME_RADIUS);
    }

    static void finish(
            SpiritEntity spirit,
            Level level,
            BlockPos feet,
            DigOutBackfill backfill,
            DigOutRestore restore,
            DigOutRollback rollback,
            BlockPlacer placer) {
        backfill.report();
        rollback.forgetReturned(restore.returnSpoil());
        rollback.close();
        PlanBreachRestore.restore(spirit, level, placer, feet);
    }
}
