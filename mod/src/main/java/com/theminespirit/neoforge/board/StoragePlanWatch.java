package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardLedger;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ChestSpace;
import com.theminespirit.core.town.SettlementReach;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.neoforge.terminal.CraftTerminalService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

final class StoragePlanWatch {
    static final List<String> LOTS = List.of("storage", "storage_2", "storage_3");
    private static final String NODE = "warehouse";

    private StoragePlanWatch() {}

    static boolean handles(String node) {
        return NODE.equals(node);
    }

    static void tick(ServerLevel level, BoardService service) {
        Optional<TownPlan> town = SettlementPlotStore.get(level).town();
        if (town.isEmpty()) {
            return;
        }
        Optional<StageSite> site = SettlementPlotStore.liveSite(level);
        if (site.isEmpty()) {
            return;
        }
        BoardLedger ledger = service.board().ledger();
        long now = level.getGameTime();
        for (String id :
                pending(ledger, town.orElseThrow(), site.orElseThrow().anchor().y())) {
            Optional<StageCatalog.Entry> entry = StageCatalog.entry(id);
            Optional<BlockCoord> origin = StageCatalog.origin(
                    id, town.orElseThrow(), site.orElseThrow().anchor().y());
            String ticketId = "storage_build:" + id;
            if (entry.isEmpty()
                    || origin.isEmpty()
                    || service.board().find(ticketId).isPresent()) {
                continue;
            }
            service.submit(
                    new BoardTicket(
                            ticketId,
                            BoardCase.BUILD_PLACED,
                            Optional.empty(),
                            BoardRules.priorityOf(BoardCase.BUILD_PLACED),
                            now,
                            1,
                            BoardTicket.ALWAYS_CAUSE,
                            List.of(),
                            Map.of()),
                    TicketTarget.at(origin.orElseThrow())
                            .withScheme(entry.orElseThrow().scheme())
                            .withBuilding(id));
        }
    }

    static boolean post(GraphPost post, GraphTickets.Ticket ticket) {
        Optional<TownPlan> town = SettlementPlotStore.get(post.level).town();
        if (town.isEmpty()) {
            return false;
        }
        BoardLedger ledger = post.service.board().ledger();
        int y = post.scan.stage().anchor().y();
        for (String id : pending(ledger, town.orElseThrow(), y)) {
            Optional<StageCatalog.Entry> entry = StageCatalog.entry(id);
            Optional<BlockCoord> origin = StageCatalog.origin(id, town.orElseThrow(), y);
            String ticketId = "storage_build:" + id;
            if (post.open(ticketId)) {
                continue;
            }
            post.submit(
                    post.ticket(
                            ticketId,
                            BoardCase.BUILD_PLACED,
                            Optional.empty(),
                            ticket.task().priority(),
                            ticket.id(),
                            Map.of()),
                    TicketTarget.at(origin.orElseThrow())
                            .withScheme(entry.orElseThrow().scheme())
                            .withBuilding(id));
        }
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

    record Space(int free, int slots) {
        int freePercent() {
            return slots == 0 ? 100 : free * 100 / slots;
        }
    }

    static Space space(ServerLevel level) {
        List<ChestSpace.Spot> spots = new ArrayList<>();
        for (ContainerSnapshot snapshot : StockReader.chests(level)) {
            BlockCoord chest = snapshot.pos();
            spots.add(new ChestSpace.Spot(chest, snapshot.freeSlots(), snapshot.slotCount()));
        }
        SettlementReach reach = CraftTerminalService.reach(level).orElse(null);
        ChestSpace space = ChestSpace.of(spots, reach);
        return new Space(space.free(), space.slots());
    }
}
