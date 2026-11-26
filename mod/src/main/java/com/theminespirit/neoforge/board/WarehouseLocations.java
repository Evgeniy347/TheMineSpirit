package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardLedger;
import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.WarehouseChests;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.FloatingBlocks;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownPlan;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class WarehouseLocations {
    private WarehouseLocations() {}

    static Set<BlockCoord> cells(ServerLevel level, BoardLedger ledger) {
        Optional<TownPlan> town = SettlementPlotStore.get(level).town();
        Optional<StageSite> site = SettlementPlotStore.liveSite(level);
        if (town.isEmpty() || site.isEmpty()) {
            return Set.of();
        }
        Set<BlockCoord> cells = new LinkedHashSet<>();
        for (String id : StoragePlanWatch.LOTS) {
            if (!ledger.hasPlan(id, 1)) {
                continue;
            }
            StageCatalog.origin(
                            id, town.orElseThrow(), site.orElseThrow().anchor().y())
                    .ifPresent(origin -> cells.addAll(WarehouseChests.cells(origin, 1)));
        }
        return cells;
    }

    static Set<BlockCoord> storeCells(ServerLevel level, BoardLedger ledger) {
        return cells(level, ledger);
    }

    static List<BlockCoord> extras(ServerLevel level, BoardLedger ledger) {
        return ChestSnapshots.positions(level).stream()
                .filter(pos -> ChestDemolition.removable(level, pos))
                .toList();
    }

    static boolean planned(ServerLevel level, BoardLedger ledger, BlockCoord cell) {
        if (cells(level, ledger).contains(cell)) {
            return true;
        }
        Optional<TownPlan> town = SettlementPlotStore.get(level).town();
        Optional<StageSite> site = SettlementPlotStore.liveSite(level);
        if (town.isEmpty() || site.isEmpty()) {
            return false;
        }
        for (var place : town.orElseThrow().placements()) {
            if (!place.mask().contains(cell.x(), cell.z())) {
                continue;
            }
            Optional<StageCatalog.Entry> entry = StageCatalog.entry(place.id());
            if (entry.isEmpty()) {
                continue;
            }
            BlockCoord origin = StageCatalog.origin(
                            place.id(),
                            town.orElseThrow(),
                            site.orElseThrow().anchor().y())
                    .orElseThrow();
            var schematic = DataTables.INSTANCE
                    .presets()
                    .current()
                    .byId(entry.orElseThrow().scheme())
                    .orElseThrow()
                    .schematic();
            BlockState actual = level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()));
            if (schematic.entries(origin).stream()
                    .anyMatch(block ->
                            block.pos().equals(cell) && containerMatches(block.block(), block.state(), actual))) {
                return true;
            }
        }
        return false;
    }

    static boolean containerMatches(String block, Map<String, String> state, BlockState actual) {
        Map<String, String> actualState = new HashMap<>();
        actual.getProperties()
                .forEach(property -> actualState.put(
                        property.getName(), actual.getValue(property).toString()));
        return containerMatches(block, state, actualState);
    }

    static boolean containerMatches(String block, Map<String, String> state, Map<String, String> actual) {
        return FloatingBlocks.containerPath(FloatingBlocks.pathOf(block)) && state.equals(actual);
    }

    static @Nullable TownPlan town(ServerLevel level) {
        return SettlementPlotStore.get(level).town().orElse(null);
    }
}
