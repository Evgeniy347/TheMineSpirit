package com.theminespirit.neoforge;

import com.theminespirit.core.building.ReachPlanner;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;

final class BuildStandPath {
    private static final int MAX_TRIES = 8;

    private BuildStandPath() {}

    static boolean known(Optional<Set<BlockCoord>> reachable) {
        return reachable.isPresent() && reachable.get().size() > 1;
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

    static Optional<BlockCoord> firstReachable(
            SpiritEntity spirit,
            List<BlockCoord> cells,
            ReachPlanner.World world,
            Set<BlockCoord> excluded,
            Set<BlockCoord> occupied,
            boolean outsideOnly,
            boolean walkKnown) {
        BlockCoord feet = new BlockCoord(
                spirit.blockPosition().getX(),
                spirit.blockPosition().getY(),
                spirit.blockPosition().getZ());
        for (int attempt = 0; attempt < MAX_TRIES; attempt++) {
            Optional<BlockCoord> found = stand(cells, world, excluded, occupied, outsideOnly, feet);
            if (found.isEmpty()) {
                return Optional.empty();
            }
            BlockCoord stand = found.get();
            if (walkKnown || pathReaches(spirit, stand)) {
                return found;
            }
            excluded.add(stand);
        }
        return Optional.empty();
    }

    static boolean plannedReachable(
            SpiritEntity spirit, SchematicSite site, BlockCoord stand, int margin, BlockCoord feet) {
        if (!site.standable(stand)) {
            return false;
        }
        Optional<Set<BlockCoord>> reachable = site.walkable(feet, margin);
        if (known(reachable)) {
            return reachable.orElseThrow().contains(stand);
        }
        return pathReaches(spirit, stand);
    }

    private static Optional<BlockCoord> stand(
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

    private static boolean pathReaches(SpiritEntity spirit, BlockCoord stand) {
        Path path = spirit.getNavigation().createPath(new BlockPos(stand.x(), stand.y(), stand.z()), 1);
        return path != null && path.canReach();
    }
}
