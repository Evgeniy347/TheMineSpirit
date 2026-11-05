package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.ProtectionConfig;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.PlotCells;
import com.theminespirit.core.town.SettlementScan;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.SettlementChestStore;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class WorldAreaScan {
    private static final Map<String, Boolean> PLANTS = new HashMap<>();
    private static final Map<String, SettlementScan.Strays> LAST = new HashMap<>();

    private WorldAreaScan() {}

    static SettlementScan.Strays scan(ServerLevel level, StageSite site, Optional<TownPlan> town) {
        BlockProtection rules = ProtectionConfig.load();
        ColumnMask area = town.isEmpty() || rules.straysInsideFenceOnly()
                ? site.enclosure()
                : town.orElseThrow().region();
        int levelY = SettlementPlotStore.get(level)
                .plot()
                .map(plot -> plot.anchor().y())
                .orElse(site.anchor().y());
        SettlementScan.Strays found =
                SettlementScan.scan(area.cells(), levelY, rules, new Live(level, site, town, levelY));
        LAST.put(key(level), found);
        return found;
    }

    static Optional<SettlementScan.Strays> last(ServerLevel level) {
        return Optional.ofNullable(LAST.get(key(level)));
    }

    static boolean demolishStrays() {
        return ProtectionConfig.load().demolishStrays();
    }

    private static String key(ServerLevel level) {
        return level.dimension().location().toString();
    }

    private static final class Live implements SettlementScan.World {
        private final ServerLevel level;
        private final StageSite site;
        private final Optional<TownPlan> town;
        private final int levelY;
        private final Set<BlockCoord> registered;

        Live(ServerLevel level, StageSite site, Optional<TownPlan> town, int levelY) {
            this.level = level;
            this.site = site;
            this.town = town;
            this.levelY = levelY;
            this.registered =
                    Set.copyOf(SettlementChestStore.get(level).chests().all());
        }

        @Override
        public boolean loaded(int x, int z) {
            return level.isLoaded(new BlockPos(x, levelY, z));
        }

        @Override
        public boolean open(int x, int y, int z) {
            BlockState state = state(x, y, z);
            return state.isAir() || !state.getFluidState().isEmpty();
        }

        @Override
        public String blockId(int x, int y, int z) {
            return BlockStates.idOf(state(x, y, z));
        }

        @Override
        public boolean planned(int x, int y, int z) {
            return WorldAreaScan.planned(site, town, levelY, x, y, z);
        }

        @Override
        public OptionalInt lotLevel(int x, int z) {
            return site.levelColumn(x, z) ? OptionalInt.of(levelY) : OptionalInt.empty();
        }

        @Override
        public OptionalInt dugTop(int x, int z) {
            return SpiritDugStore.get(level).top(x, z);
        }

        @Override
        public boolean playerPlaced(int x, int y, int z) {
            return PlacedBlocksData.get(level).isMarked(new BlockPos(x, y, z));
        }

        @Override
        public boolean spiritMarked(int x, int y, int z) {
            return PlacedBlocksData.get(level).isSpiritMarked(new BlockPos(x, y, z));
        }

        @Override
        public boolean registered(int x, int y, int z) {
            return registered.contains(new BlockCoord(x, y, z));
        }

        @Override
        public boolean plant(String blockId) {
            return PLANTS.computeIfAbsent(blockId, WorldAreaScan::plant);
        }

        private BlockState state(int x, int y, int z) {
            return level.getBlockState(new BlockPos(x, y, z));
        }
    }

    private static boolean planned(StageSite site, Optional<TownPlan> town, int levelY, int x, int y, int z) {
        if (site.planCell(x, y, z)) {
            return true;
        }
        if (town.isEmpty()) {
            return false;
        }
        TownPlan plan = town.orElseThrow();
        if (plan.streets().contains(x, z) && y >= levelY - 1 && y <= levelY + PlotCells.FREE_HEIGHT) {
            return true;
        }
        for (Placement place : plan.upToStage(site.stage())) {
            if (place.covers(x, z) && y >= levelY - 1 && y <= levelY + StageSite.BUILDING_HEIGHT) {
                return true;
            }
        }
        return false;
    }

    private static boolean plant(String blockId) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null) {
            return false;
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null) {
            return false;
        }
        BlockState state = block.defaultBlockState();
        return state.is(BlockTags.LEAVES)
                || state.is(BlockTags.LOGS)
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.CROPS)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.VINE)
                || state.is(Blocks.LILY_PAD)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.BAMBOO)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.SUGAR_CANE);
    }
}
