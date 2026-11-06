package com.theminespirit.neoforge;

import com.theminespirit.core.building.ReachPlanner;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.building.StandPathGate;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class BuildStandPath {
    private BuildStandPath() {}

    static Optional<BlockCoord> choose(
            List<BlockCoord> cells,
            ReachPlanner.World world,
            Set<BlockCoord> excluded,
            Set<BlockCoord> occupied,
            boolean outsideOnly,
            BlockCoord feet) {
        Optional<BlockCoord> near = outsideOnly
                ? Optional.empty()
                : ReachPlanner.nextStand(SpiritReach.current(), feet, cells, world, excluded, occupied);
        return near.isPresent()
                ? near
                : ReachPlanner.nextStandFromAfar(SpiritReach.current(), feet, cells, world, excluded, occupied);
    }

    static boolean plannedReachable(SchematicSite site, BlockCoord stand, int margin, BlockCoord feet) {
        return StandPathGate.usable(
                site.standable(stand), site.networkBox(margin).contains(feet), site.walkable(feet, margin), stand);
    }

    static ReachPlanner.World walkableOnly(ReachPlanner.World world, Set<BlockCoord> cells) {
        return new ReachPlanner.World() {
            @Override
            public boolean ready(BlockCoord cell) {
                return world.ready(cell);
            }

            @Override
            public boolean standable(BlockCoord feet) {
                return cells.contains(feet) && world.standable(feet);
            }
        };
    }
}
