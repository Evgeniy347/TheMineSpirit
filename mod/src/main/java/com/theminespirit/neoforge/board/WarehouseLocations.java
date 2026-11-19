package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardLedger;
import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.WarehouseChests;
import com.theminespirit.core.board.WarehouseOrder;
import com.theminespirit.core.board.WarehouseRules;
import com.theminespirit.core.board.api.StorageCells;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.YardScheme;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

final class WarehouseLocations {
    private static final String STORAGE = "storage";

    private WarehouseLocations() {}

    static Optional<BlockCoord> origin(ServerLevel level, BoardLedger ledger) {
        int built = WarehouseRules.builtLevel(ledger);
        if (built == 0) {
            return Optional.empty();
        }
        WarehouseOrder order = WarehouseOrderStore.get(level).order();
        if (order != null) {
            return Optional.of(order.site());
        }
        return planSite(level);
    }

    static Optional<BlockCoord> planSite(ServerLevel level) {
        SettlementPlotStore store = SettlementPlotStore.get(level);
        Optional<TownPlan> town = store.town();
        Optional<StageSite> site = store.site();
        if (town.isEmpty() || site.isEmpty()) {
            return Optional.empty();
        }
        return StageCatalog.origin(
                STORAGE, town.orElseThrow(), site.orElseThrow().anchor().y());
    }

    static Set<BlockCoord> cells(ServerLevel level, BoardLedger ledger) {
        Optional<BlockCoord> origin = origin(level, ledger);
        int built = WarehouseRules.builtLevel(ledger);
        if (origin.isEmpty() || built == 0) {
            return Set.of();
        }
        return new LinkedHashSet<>(WarehouseChests.cells(origin.orElseThrow(), built));
    }

    static Set<BlockCoord> storeCells(ServerLevel level, BoardLedger ledger) {
        Set<BlockCoord> warehouse = cells(level, ledger);
        return StorageCells.storage(warehouse, yardCells(level));
    }

    static Set<BlockCoord> yardCells(ServerLevel level) {
        Optional<StageSite> site = SettlementPlotStore.liveSite(level);
        if (site.isEmpty()) {
            return Set.of();
        }
        StageSite place = site.orElseThrow();
        Set<BlockCoord> cells = new LinkedHashSet<>();
        for (List<BlockCoord> pair : YardScheme.slots(place)) {
            if (supported(level, place, pair)) {
                cells.addAll(pair);
            }
        }
        for (List<BlockCoord> pair : YardScheme.overflowSlots(place)) {
            if (supported(level, place, pair)) {
                cells.addAll(pair);
            }
        }
        return cells;
    }

    static boolean onSettlementFloor(ServerLevel level, StageSite site, BlockCoord cell) {
        return cell.y() == site.anchor().y() && aboveGround(level, cell);
    }

    static boolean aboveGround(ServerLevel level, BlockCoord cell) {
        return !level.getBlockState(new BlockPos(cell.x(), cell.y() - 1, cell.z()))
                .isAir();
    }

    private static boolean supported(ServerLevel level, StageSite site, List<BlockCoord> pair) {
        return pair.stream().allMatch(cell -> onSettlementFloor(level, site, cell));
    }

    static List<BlockCoord> extras(ServerLevel level, BoardLedger ledger) {
        return ChestSnapshots.positions(level).stream()
                .filter(pos -> ChestDemolition.removable(level, pos))
                .toList();
    }

    static boolean planned(ServerLevel level, BoardLedger ledger, BlockCoord cell) {
        if (yardCells(level).contains(cell)
                || cells(level, ledger).contains(cell)
                || PlotCellStructure.plannedChest(level, cell)) {
            return true;
        }
        WarehouseOrder order = WarehouseOrderStore.get(level).order();
        if (order != null && WarehouseChests.cells(order.site(), order.level()).contains(cell)) {
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
            if (schematic.entries(origin).stream()
                    .anyMatch(block -> block.pos().equals(cell) && block.block().endsWith("chest"))) {
                return true;
            }
        }
        return false;
    }

    static @Nullable TownPlan town(ServerLevel level) {
        return SettlementPlotStore.get(level).town().orElse(null);
    }
}
