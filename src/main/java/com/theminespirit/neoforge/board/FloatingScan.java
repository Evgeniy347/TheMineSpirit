package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.SettlementPlot;
import com.theminespirit.core.forestry.TreeGroup;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.StandSpots;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.ProtectionConfig;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.FloatingBlocks;
import com.theminespirit.core.town.FloatingWindow;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StrayRule;
import com.theminespirit.core.town.TreeSupport;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.LevelTreeView;
import com.theminespirit.neoforge.PlotGround;
import com.theminespirit.neoforge.SettlementChestStore;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public final class FloatingScan {
    private static final String AIR = "minecraft:air";
    private static final BlockProtection PROTECTION = ProtectionConfig.load();

    private FloatingScan() {}

    public static List<FloatingBlocks.Finding> find(ServerLevel level, StageSite site, ColumnMask region) {
        Optional<SettlementPlot> plot = SettlementPlotStore.get(level).plot();
        if (plot.isEmpty()) {
            return List.of();
        }
        SettlementPlot measured = plot.orElseThrow();
        return find(level, site, region, FloatingWindow.bottomOf(measured), FloatingWindow.topOf(measured));
    }

    public static List<FloatingBlocks.Finding> find(
            ServerLevel level, StageSite site, ColumnMask region, int bottom, int top) {
        ColumnMask area = site.enclosure().intersect(region);
        if (area.isEmpty()) {
            return List.of();
        }
        Scan scan = new Scan(level, PlotPlanBlocks.of(site));
        List<FloatingBlocks.Finding> found = new ArrayList<>();
        Set<BlockCoord> seen = new LinkedHashSet<>();
        for (FloatingBlocks.Finding finding : FloatingBlocks.find(area, bottom, top, scan)) {
            if (!seen.add(finding.at())) {
                continue;
            }
            classifyTree(finding, scan, seen, found);
        }
        for (BlockCoord cell : placed(level, area)) {
            if (cell.y() >= bottom && cell.y() <= top && area.contains(cell.x(), cell.z()) && seen.add(cell)) {
                FloatingBlocks.classify(cell, scan).ifPresent(finding -> classifyTree(finding, scan, seen, found));
            }
        }
        return List.copyOf(found);
    }

    private static void classifyTree(
            FloatingBlocks.Finding finding, Scan scan, Set<BlockCoord> seen, List<FloatingBlocks.Finding> found) {
        if (finding.kind() != FloatingBlocks.Kind.TREE) {
            found.add(finding);
            return;
        }
        Optional<TreeGroup> group = TreeGroup.of(finding.at(), scan.trees());
        if (group.isEmpty()) {
            found.add(finding);
            return;
        }
        if (group.get().grounded()) {
            return;
        }
        BlockCoord base = group.get().base();
        seen.addAll(group.get().cells());
        int ground = scan.groundBelow(base);
        found.add(new FloatingBlocks.Finding(
                base,
                FloatingBlocks.Kind.TREE,
                scan.blockId(base),
                Math.max(0, base.y() - ground - 1),
                "бревно висящего дерева без опоры, вне плана",
                List.copyOf(group.get().cells())));
    }

    public static @Nullable BlockCoord standBeside(ServerLevel level, List<BlockCoord> cells) {
        LevelSafetyView safety = new LevelSafetyView(level);
        for (BlockCoord column : TreeSupport.candidates(cells)) {
            int ground = groundBelow(level, column);
            BlockCoord stand = new BlockCoord(column.x(), ground + 1, column.z());
            if (MiningSafety.canStand(stand, safety)) {
                return stand;
            }
        }
        return null;
    }

    public static boolean reachable(ServerLevel level, BlockCoord at) {
        return !StandSpots.of(at, new LevelSafetyView(level), at).isEmpty();
    }

    private static int groundBelow(ServerLevel level, BlockCoord base) {
        for (int y = base.y() - 1; y > level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(base.x(), y, base.z());
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return y;
            }
        }
        return level.getMinBuildHeight();
    }

    private static List<BlockCoord> placed(ServerLevel level, ColumnMask area) {
        PlacedBlocksData placed = PlacedBlocksData.get(level);
        List<BlockCoord> cells = new ArrayList<>();
        int x1 = area.minX() >> 4;
        int z1 = area.minZ() >> 4;
        int x2 = (area.minX() + area.width() - 1) >> 4;
        int z2 = (area.minZ() + area.depth() - 1) >> 4;
        for (int cx = x1; cx <= x2; cx++) {
            for (int cz = z1; cz <= z2; cz++) {
                cells.addAll(placed.spiritMarkedIn(cx, cz));
            }
        }
        return cells;
    }

    private static final class Scan implements FloatingBlocks.World {
        private static final Set<Block> LOGS = BuiltInRegistries.BLOCK.stream()
                .filter(block -> block.defaultBlockState().is(BlockTags.LOGS))
                .collect(Collectors.toUnmodifiableSet());
        private final ServerLevel level;
        private final PlotPlanBlocks.Plan plan;
        private final LevelTreeView trees;
        private final Set<BlockCoord> registered;

        Scan(ServerLevel level, PlotPlanBlocks.Plan plan) {
            this.level = level;
            this.plan = plan;
            this.trees = new LevelTreeView(level, LOGS);
            this.registered =
                    Set.copyOf(SettlementChestStore.get(level).chests().all());
        }

        LevelTreeView trees() {
            return trees;
        }

        int groundBelow(BlockCoord base) {
            for (int y = base.y() - 1; y > level.getMinBuildHeight(); y--) {
                BlockPos pos = new BlockPos(base.x(), y, base.z());
                if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                    return y;
                }
            }
            return level.getMinBuildHeight();
        }

        @Override
        public String blockId(BlockCoord at) {
            BlockPos pos = pos(at);
            if (!level.isLoaded(pos)) {
                return AIR;
            }
            return BlockStates.idOf(level.getBlockState(pos));
        }

        @Override
        public boolean natural(String blockId) {
            return PROTECTION.natural(blockId) || PlotGround.natural(BlockStates.stateFor(blockId));
        }

        @Override
        public boolean planned(BlockCoord at) {
            return plan.planned(at);
        }

        @Override
        public boolean log(BlockCoord at) {
            return level.getBlockState(pos(at)).is(BlockTags.LOGS);
        }

        @Override
        public boolean treeLog(BlockCoord at) {
            return !StrayRule.treeLogs(at, trees).isEmpty();
        }

        @Override
        public boolean supported(BlockCoord at) {
            BlockPos pos = pos(at);
            BlockPos below = pos.below();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return true;
            }
            BlockState state = level.getBlockState(pos);
            if (!wallMounted(FloatingBlocks.pathOf(BlockStates.idOf(state)))) {
                return false;
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos side = pos.relative(direction);
                if (level.getBlockState(side).isFaceSturdy(level, side, direction.getOpposite())
                        && state.canSurvive(level, pos)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean registeredChest(BlockCoord at) {
            return registered.contains(at);
        }

        @Override
        public int groundY(int x, int z) {
            for (int y = level.getMaxBuildHeight() - 1; y > level.getMinBuildHeight(); y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                    return y;
                }
            }
            return level.getMinBuildHeight();
        }

        private static boolean wallMounted(String path) {
            return path.endsWith("_torch")
                    || path.endsWith("_lantern")
                    || path.endsWith("_sign")
                    || path.endsWith("_wall_sign")
                    || path.endsWith("_button")
                    || path.endsWith("_banner")
                    || "ladder".equals(path)
                    || "lever".equals(path);
        }

        private static BlockPos pos(BlockCoord at) {
            return new BlockPos(at.x(), at.y(), at.z());
        }
    }
}
