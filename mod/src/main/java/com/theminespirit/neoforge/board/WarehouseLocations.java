package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardLedger;
import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.WarehouseChests;
import com.theminespirit.core.board.WarehouseOrder;
import com.theminespirit.core.board.WarehouseRules;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownPlan;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    static List<BlockCoord> extras(ServerLevel level, BoardLedger ledger) {
        Set<BlockCoord> cells = cells(level, ledger);
        if (cells.isEmpty()) {
            return List.of();
        }
        return ChestSnapshots.positions(level).stream()
                .filter(pos -> !cells.contains(pos))
                .toList();
    }

    static @Nullable TownPlan town(ServerLevel level) {
        return SettlementPlotStore.get(level).town().orElse(null);
    }
}
