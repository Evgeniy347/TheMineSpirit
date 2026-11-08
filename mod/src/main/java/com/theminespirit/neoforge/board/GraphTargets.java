package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.MineDescentBottom;
import com.theminespirit.core.board.WorkStand;
import com.theminespirit.core.board.graph.FailureTargets;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.pickup.LeafDropStand;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CraftingTableBlock;

final class GraphTargets {
    private static final int STATION_RADIUS = 12;
    private static final int STATION_HEIGHT = 4;
    private static final int NO_GROUND = Integer.MIN_VALUE;

    private GraphTargets() {}

    static BlockCoord stand(ServerLevel level, BlockCoord point) {
        return WorkStand.near(point, ground(level)).orElse(point);
    }

    static Optional<BlockCoord> standNear(ServerLevel level, BlockCoord point) {
        return WorkStand.near(point, ground(level));
    }

    static Optional<BlockCoord> leafDropStand(ServerLevel level, BlockCoord leaf) {
        return LeafDropStand.of(leaf, ground(level));
    }

    static BlockCoord source(
            ServerLevel level,
            Optional<SupplyGraph.Way> way,
            GraphSurroundings.Around around,
            FirstStepsRunner.StageScan scan,
            BoardService service,
            SupplyGraph graph,
            long now) {
        double halfLife = graph.costs().choice().failureHalfLife();
        Set<String> unreachable = service.sources.graphUnreachablePoints(now, halfLife);
        double radius = graph.costs().choice().failureTargetRadius();
        Optional<BlockCoord> resource = way.map(SupplyGraph.Way::world).flatMap(around::resource);
        if (resource.isPresent()) {
            String world = way.map(SupplyGraph.Way::world).orElse(null);
            List<BlockCoord> candidates = world == null ? List.of() : around.targets(world);
            List<BlockCoord> known = candidates.isEmpty() ? List.of(resource.orElseThrow()) : candidates;
            List<BlockCoord> stands = known.stream().map(at -> stand(level, at)).toList();
            return FailureTargets.choose(stands, unreachable, radius);
        }
        String building = way.map(SupplyGraph.Way::building).orElse(null);
        if (building == null) {
            return craftPoint(level, scan);
        }
        if ("mine".equals(building)) {
            return stand(level, scan.stage().mineEntrance());
        }
        if (building.startsWith("mine_level_")) {
            return mineBottom(level, scan).orElseGet(() -> craftPoint(level, scan));
        }
        return craftPoint(level, scan);
    }

    private static BlockCoord craftPoint(ServerLevel level, FirstStepsRunner.StageScan scan) {
        boolean yard = scan.facts().done().contains(BoardCase.STOCK_CHESTS);
        return craft(level, yard ? scan.stage().workYard() : scan.stage().anchor());
    }

    private static Optional<BlockCoord> mineBottom(ServerLevel level, FirstStepsRunner.StageScan scan) {
        try {
            return MineDescentBottom.standOf(scan.stage().mineEntrance()).map(bottom -> stand(level, bottom));
        } catch (IllegalStateException | IllegalArgumentException unbuildable) {
            return Optional.empty();
        }
    }

    static BlockCoord craft(ServerLevel level, BlockCoord center) {
        BlockCoord station = find(level, center, CraftingTableBlock.class)
                .or(() -> find(level, center, ChestBlock.class))
                .orElse(center);
        return stand(level, station);
    }

    private static Optional<BlockCoord> find(ServerLevel level, BlockCoord center, Class<? extends Block> wanted) {
        BlockCoord best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int dx = -STATION_RADIUS; dx <= STATION_RADIUS; dx++) {
            for (int dz = -STATION_RADIUS; dz <= STATION_RADIUS; dz++) {
                for (int dy = -STATION_HEIGHT; dy <= STATION_HEIGHT; dy++) {
                    BlockPos pos = new BlockPos(center.x() + dx, center.y() + dy, center.z() + dz);
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    boolean match = wanted.isInstance(level.getBlockState(pos).getBlock());
                    long distance = (long) dx * dx + (long) dy * dy + (long) dz * dz;
                    if (match && distance < bestDistance) {
                        bestDistance = distance;
                        best = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static WorkStand.Ground ground(ServerLevel level) {
        return new WorkStand.Ground() {
            @Override
            public int feet(int x, int z) {
                return GroundProbe.feet(level, x, z, NO_GROUND);
            }

            @Override
            public boolean free(BlockCoord feet) {
                if (feet.y() == NO_GROUND) {
                    return false;
                }
                BlockPos pos = new BlockPos(feet.x(), feet.y(), feet.z());
                return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                        && level.getBlockState(pos.above())
                                .getCollisionShape(level, pos.above())
                                .isEmpty()
                        && level.getFluidState(pos).isEmpty()
                        && !level.getBlockState(pos.below())
                                .getCollisionShape(level, pos.below())
                                .isEmpty();
            }
        };
    }
}
