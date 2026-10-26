package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.SettlementPlot;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.ProtectionConfig;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StrayRule;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.LevelTreeView;
import com.theminespirit.neoforge.PlotGround;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class StrayBlocks {
    private static final int BELOW = 2;
    private static final int ABOVE = StageSite.BUILDING_HEIGHT + 3;
    private static final BlockProtection PROTECTION = ProtectionConfig.load();

    private StrayBlocks() {}

    public static List<BlockCoord> find(ServerLevel level, StageSite site, ColumnMask region) {
        PlacedBlocksData placed = PlacedBlocksData.get(level);
        Scan scan = new Scan(level, site, placed);
        Set<BlockCoord> stray = new LinkedHashSet<>();
        int chunkX1 = region.minX() >> 4;
        int chunkZ1 = region.minZ() >> 4;
        int chunkX2 = (region.minX() + region.width() - 1) >> 4;
        int chunkZ2 = (region.minZ() + region.depth() - 1) >> 4;
        for (int cx = chunkX1; cx <= chunkX2; cx++) {
            for (int cz = chunkZ1; cz <= chunkZ2; cz++) {
                for (BlockCoord cell : placed.spiritMarkedIn(cx, cz)) {
                    if (region.contains(cell.x(), cell.z()) && scan.stray(cell)) {
                        stray.add(cell);
                    }
                }
            }
        }
        Optional<SettlementPlot> plot = SettlementPlotStore.get(level).plot();
        if (plot.isPresent()) {
            int bottom = plot.orElseThrow().min() - BELOW;
            int top = plot.orElseThrow().max() + ABOVE;
            for (ColumnMask.Cell column : site.enclosureCells()) {
                if (!region.contains(column.x(), column.z())
                        || !level.isLoaded(new BlockPos(column.x(), bottom, column.z()))) {
                    continue;
                }
                for (int y = bottom; y <= top; y++) {
                    BlockCoord cell = new BlockCoord(column.x(), y, column.z());
                    if (scan.stray(cell)) {
                        stray.add(cell);
                    }
                }
            }
        }
        return List.copyOf(new ArrayList<>(stray));
    }

    private static final class Scan {
        private static final Set<Block> LOGS = BuiltInRegistries.BLOCK.stream()
                .filter(block -> block.defaultBlockState().is(BlockTags.LOGS))
                .collect(Collectors.toUnmodifiableSet());
        private final ServerLevel level;
        private final StageSite site;
        private final PlacedBlocksData placed;
        private final LevelTreeView trees;
        private final Map<BlockCoord, Boolean> inTree = new HashMap<>();

        Scan(ServerLevel level, StageSite site, PlacedBlocksData placed) {
            this.level = level;
            this.site = site;
            this.placed = placed;
            this.trees = new LevelTreeView(level, LOGS);
        }

        boolean stray(BlockCoord cell) {
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                return false;
            }
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof Container container && !container.isEmpty()) {
                return false;
            }
            return StrayRule.stray(BlockStates.idOf(state), facts(cell, pos, state), PROTECTION);
        }

        private Set<StrayRule.Fact> facts(BlockCoord cell, BlockPos pos, BlockState state) {
            Set<StrayRule.Fact> facts = EnumSet.noneOf(StrayRule.Fact.class);
            if (site.planned(cell.x(), cell.y(), cell.z())) {
                facts.add(StrayRule.Fact.PLANNED);
            }
            boolean spirit = placed.isSpiritMarked(pos);
            if (spirit) {
                facts.add(StrayRule.Fact.SPIRIT);
            } else if (placed.isMarked(pos)) {
                facts.add(StrayRule.Fact.PLAYER);
            }
            if (state.is(BlockTags.SAPLINGS) || state.is(BlockTags.FLOWERS) || state.canBeReplaced()) {
                facts.add(StrayRule.Fact.PLANT);
            }
            if (state.is(BlockTags.LOGS)) {
                facts.add(StrayRule.Fact.LOG);
                if (!spirit && tree(cell)) {
                    facts.add(StrayRule.Fact.IN_TREE);
                }
            }
            if (PlotGround.natural(state)) {
                facts.add(StrayRule.Fact.GROUND);
            }
            if (facts.contains(StrayRule.Fact.GROUND) || PROTECTION.natural(BlockStates.idOf(state))) {
                if (level.getBlockState(pos.below())
                        .getCollisionShape(level, pos.below())
                        .isEmpty()) {
                    facts.add(StrayRule.Fact.UNSUPPORTED);
                }
            }
            return facts;
        }

        private boolean tree(BlockCoord cell) {
            Boolean known = inTree.get(cell);
            if (known != null) {
                return known;
            }
            Set<BlockCoord> logs = StrayRule.treeLogs(cell, trees);
            logs.forEach(log -> inTree.put(log, true));
            inTree.putIfAbsent(cell, false);
            return inTree.get(cell);
        }
    }
}
