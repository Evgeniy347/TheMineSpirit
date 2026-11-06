package com.theminespirit.neoforge.board;

import com.theminespirit.core.fishing.FishingData;
import com.theminespirit.core.fishing.FishingSpots;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.BlockLocator;
import com.theminespirit.neoforge.FishingWorkPointPicker;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

final class FishingSite {
    private static final int STATION_RADIUS = 8;
    private static final int STATION_BUDGET = 4096;
    private static final int STATION_SLICES = 16;
    private static final int WORK_POINT_RADIUS = 32;
    private static final double WORK_POINT_AVOID = 3.0;
    private static final Set<Block> SMOKERS = Set.of(Blocks.SMOKER);

    private FishingSite() {}

    static boolean smokerNear(ServerLevel level, BlockCoord home) {
        BlockLocator.Search search =
                BlockLocator.search(level, new BlockPos(home.x(), home.y(), home.z()), STATION_RADIUS, SMOKERS);
        while (!search.done() && search.examinedPositions() < STATION_BUDGET) {
            if (!search.step(STATION_SLICES).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static Optional<BlockCoord> workPoint(ServerLevel level, BlockCoord home) {
        FishingData data = DataTables.INSTANCE.fishing().current();
        return FishingWorkPointPicker.pick(level, home, data, WORK_POINT_RADIUS, List.of(), WORK_POINT_AVOID)
                .map(FishingSpots.Spot::stand);
    }
}
