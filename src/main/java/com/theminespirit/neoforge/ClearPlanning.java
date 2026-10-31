package com.theminespirit.neoforge;

import com.theminespirit.core.mining.MiningPlanner;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;

final class ClearPlanning {
    private ClearPlanning() {}

    static MiningPlanner.Outcome next(
            SpiritEntity spirit, BlockCoord from, List<BlockCoord> cells, SafetyView safety, Set<BlockCoord> excluded) {
        return MiningPlanner.nextReachable(from, cells, safety, excluded, stand -> reachable(spirit, stand));
    }

    private static boolean reachable(SpiritEntity spirit, BlockCoord stand) {
        Path path = spirit.getNavigation().createPath(new BlockPos(stand.x(), stand.y(), stand.z()), 1);
        return path != null && path.canReach();
    }
}
