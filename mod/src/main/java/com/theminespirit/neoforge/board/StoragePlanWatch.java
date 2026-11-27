package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardLedger;
import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ChestSpace;
import com.theminespirit.core.town.SettlementReach;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.neoforge.terminal.CraftTerminalService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

final class StoragePlanWatch {
    static final List<String> LOTS = List.of("storage", "storage_2", "storage_3");
    private static final String NODE = "warehouse";

    private StoragePlanWatch() {}

    static boolean handles(String node) {
        return NODE.equals(node);
    }

    static boolean post(GraphPost post, GraphTickets.Ticket ticket) {
        Optional<TownPlan> town = SettlementPlotStore.get(post.level).town();
        if (town.isEmpty()) {
            return false;
        }
        BoardLedger ledger = post.service.board().ledger();
        int y = post.scan.stage().anchor().y();
        Optional<String> next = next(pending(ledger, town.orElseThrow(), y), id -> post.open("storage_build:" + id));
        if (next.isEmpty()) {
            return true;
        }
        String id = next.orElseThrow();
        Optional<StageCatalog.Entry> entry = StageCatalog.entry(id);
        Optional<BlockCoord> origin = StageCatalog.origin(id, town.orElseThrow(), y);
        post.submit(
                post.ticket(
                        "storage_build:" + id,
                        BoardCase.BUILD_PLACED,
                        Optional.empty(),
                        ticket.task().priority(),
                        ticket.id(),
                        Map.of()),
                StagePlacePost.level(
                        TicketTarget.at(origin.orElseThrow())
                                .withScheme(entry.orElseThrow().scheme())
                                .withBuilding(id),
                        post,
                        id));
        return true;
    }

    static List<String> pending(BoardLedger ledger, TownPlan town, int y) {
        List<String> ids = new ArrayList<>();
        for (String id : LOTS) {
            if (ledger.hasPlan(id, 1)) {
                continue;
            }
            if (StageCatalog.entry(id).isPresent()
                    && StageCatalog.origin(id, town, y).isPresent()) {
                ids.add(id);
            }
        }
        return ids;
    }

    static Optional<String> next(List<String> pending, Predicate<String> open) {
        return pending.stream().anyMatch(open)
                ? Optional.empty()
                : pending.stream().findFirst();
    }

    record Space(int free, int slots) {
        int freePercent() {
            return slots == 0 ? 100 : free * 100 / slots;
        }
    }

    static Space space(ServerLevel level, BoardLedger ledger) {
        Set<BlockCoord> cells = WarehouseLocations.storeCells(level, ledger);
        List<ContainerSnapshot> chests = ChestSnapshots.read(level, List.copyOf(cells));
        SettlementReach reach = CraftTerminalService.reach(level).orElse(null);
        return space(chests, cells, reach);
    }

    static Space space(List<ContainerSnapshot> chests, Set<BlockCoord> cells) {
        return space(chests, cells, null);
    }

    private static Space space(List<ContainerSnapshot> chests, Set<BlockCoord> cells, @Nullable SettlementReach reach) {
        List<ChestSpace.Spot> spots = new ArrayList<>();
        for (ContainerSnapshot snapshot : chests) {
            if (!cells.contains(snapshot.pos())) {
                continue;
            }
            BlockCoord chest = snapshot.pos();
            spots.add(new ChestSpace.Spot(chest, snapshot.freeSlots(), snapshot.slotCount()));
        }
        ChestSpace space = ChestSpace.of(spots, reach);
        return new Space(space.free(), space.slots());
    }
}
