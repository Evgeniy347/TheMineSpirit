package com.theminespirit.neoforge;

import com.theminespirit.core.movement.PitWalkOut;
import com.theminespirit.core.movement.WalkOutRules;
import com.theminespirit.neoforge.board.PitWalkOutView;
import com.theminespirit.neoforge.policy.RescueBreach;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

final class DigOutExit {
    private DigOutExit() {}

    static boolean fixedCeiling(Level level, BlockPos feet) {
        BlockPos ceiling = feet.above(2);
        return !DigOutBackfill.passable(level, ceiling) && !RescueBreach.breakable(level.getBlockState(ceiling));
    }

    static boolean surfaced(Level level, BlockPos feet, DigOutRestore restore) {
        return DigOutScript.open(level, feet)
                && !restore.pendingOver(feet)
                && PitWalkOut.exists(
                        new PitWalkOutView(level, new DigOutPendingView(level, restore)),
                        WalkOutRules.current(),
                        feet.getX(),
                        feet.getZ(),
                        feet.getY());
    }
}
