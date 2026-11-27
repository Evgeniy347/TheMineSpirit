package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.audit.DiffItem;
import com.theminespirit.core.board.audit.Group;
import com.theminespirit.core.board.audit.GroupRegistry;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.PlotCells;
import com.theminespirit.core.town.SettlementScan;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StrayGroups;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.SettlementChestStore;
import com.theminespirit.neoforge.lease.LeaseData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

final class WorldAreaScan {
    private static final Map<String, Boolean> PLANTS = new HashMap<>();

    private WorldAreaScan() {}

    static SettlementScan.Strays scan(ServerLevel level, StageSite site, Optional<TownPlan> town) {
        SettlementScan.Strays found = scanOrNull(level, site, town);
        return found == null ? SettlementScan.Strays.unmeasured() : found;
    }

    static SettlementScan.@Nullable Strays scanOrNull(ServerLevel level, StageSite site, Optional<TownPlan> town) {
        return fromGroups(level);
    }

    private static SettlementScan.@Nullable Strays fromGroups(ServerLevel level) {
        GroupRegistry registry = TownAuditWatch.peekGroups(level);
        if (registry == null) {
            return null;
        }
        Map<BlockCoord, Integer> pits = new LinkedHashMap<>();
        List<StrayGroups.Cell> strays = new ArrayList<>();
        int unknown = 0;
        for (Group group : registry.groups()) {
            unknown += group.unknown();
            if (group.kind() != Group.Kind.GROUND) {
                continue;
            }
            for (DiffItem item : group.items()) {
                if ("pit".equals(group.subject()) && item.action() != DiffItem.Action.BREAK) {
                    pits.put(item.cell(), 1);
                } else if (("stray".equals(group.subject()) || "floating".equals(group.subject()))
                        && item.action() == DiffItem.Action.BREAK) {
                    strays.add(new StrayGroups.Cell(item.cell(), item.found()));
                }
            }
        }
        return new SettlementScan.Strays(pits, List.of(), List.of(), strays, unknown, 0);
    }

    static SettlementScan.World view(ServerLevel level, StageSite site) {
        int levelY = SettlementPlotStore.get(level)
                .plot()
                .map(plot -> plot.anchor().y())
                .orElse(site.anchor().y());
        return new Live(level, site, SettlementPlotStore.get(level).town(), levelY);
    }

    static Optional<SettlementScan.Strays> last(ServerLevel level) {
        return Optional.ofNullable(fromGroups(level));
    }

    static SettlementScan.Strays cachedOrScan(ServerLevel level, StageSite site) {
        return scan(level, site, SettlementPlotStore.get(level).town());
    }

    static boolean demolishStrays() {
        return DataTables.INSTANCE.protection().current().demolishStrays();
    }

    static List<BlockCoord> raised(SettlementScan.Strays found) {
        List<BlockCoord> cells = new ArrayList<>(found.mounds());
        cells.addAll(found.pillars());
        found.strays().forEach(stray -> cells.add(stray.at()));
        return cells;
    }

    static List<BlockCoord> holes(SettlementScan.Strays found) {
        return List.copyOf(found.pits().keySet());
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
        public int top(int x, int z) {
            return level.isLoaded(new BlockPos(x, levelY, z))
                    ? level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)
                    : levelY;
        }

        @Override
        public boolean planned(int x, int y, int z) {
            return WorldAreaScan.planned(site, town, levelY, x, y, z);
        }

        @Override
        public OptionalInt lotLevel(int x, int z) {
            return site.zone().contains(x, z) ? OptionalInt.of(levelY) : OptionalInt.empty();
        }

        @Override
        public OptionalInt dugTop(int x, int z) {
            return SpiritDugStore.get(level).top(x, z);
        }

        @Override
        public boolean temporary(int x, int y, int z) {
            BlockPos pos = new BlockPos(x, y, z);
            LeaseData leases = LeaseData.get(level);
            if (leases.isActive(pos)) {
                return false;
            }
            return leases.isOrphaned(pos);
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
