package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.ResourceGate;
import com.theminespirit.core.board.StockNeed;
import com.theminespirit.core.board.StockParts;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.SupplyGate;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;

final class BoardSupplyDesk {
    private final Board board;
    private final StockReader reader;
    private final Map<String, TicketTarget> targets;
    private final Map<String, Long> closed = new HashMap<>();
    private StockView stock = new StockView(Map.of());

    BoardSupplyDesk(Board board, StockReader reader, Map<String, TicketTarget> targets) {
        this.board = board;
        this.reader = reader;
        this.targets = targets;
    }

    StockView stock() {
        return stock;
    }

    Map<String, Long> closed() {
        return closed;
    }

    StockParts refresh(ServerLevel level, List<SpiritEntity> spirits) {
        StockReader.Snapshot snapshot = reader.collect(level, spirits, gateCenters(spirits));
        stock = snapshot.parts().total();
        return snapshot.parts();
    }

    void ensure(ServerLevel level, BoardService service, List<SpiritEntity> spirits, long now) {
        if (spirits.isEmpty()) {
            return;
        }
        Map<String, Integer> missing = new HashMap<>();
        for (BoardTicket ticket : board.tickets()) {
            BoardStatus status = board.statusOf(ticket.id()).orElse(null);
            if (status != BoardStatus.PENDING && status != BoardStatus.WAITING_RESOURCES) {
                continue;
            }
            ResourceGate.missing(new StockNeed(ticket.requiredItems()), stock).forEach((item, count) -> {
                if (!hasActiveSupply(item)) {
                    missing.merge(item, count, Integer::sum);
                }
            });
        }
        SupplyGate.Decision decision = SupplyGate.decide(
                missing,
                stock.available(),
                DataTables.INSTANCE.leveling().current().chestKeep(),
                closed,
                now,
                BoardTuning.failBackoffTicks());
        BlockCoord home = SettlementHome.of(spirits);
        for (SupplyGate.Request request : decision.supply()) {
            SupplyTickets.issue(level, service, targets, home, request, BoardCase.SUPPLY_SITE, now);
        }
        for (SupplyGate.Request request : decision.gather()) {
            SupplyTickets.issue(level, service, targets, home, request, BoardCase.GATHER_MATERIALS, now);
        }
    }

    private List<BlockCoord> gateCenters(List<SpiritEntity> spirits) {
        List<BlockCoord> centers = new ArrayList<>();
        for (BoardTicket ticket : board.tickets()) {
            BoardStatus status = board.statusOf(ticket.id()).orElse(null);
            if (status != BoardStatus.PENDING && status != BoardStatus.WAITING_RESOURCES) {
                continue;
            }
            if (ticket.requiredItems().isEmpty()) {
                continue;
            }
            TicketTarget target = targets.get(ticket.id());
            BlockCoord center = target != null ? target.target() : SettlementHome.of(spirits);
            if (!centers.contains(center)) {
                centers.add(center);
            }
        }
        if (centers.isEmpty()) {
            centers.add(SettlementHome.of(spirits));
        }
        return centers;
    }

    private boolean hasActiveSupply(String item) {
        return SupplyGate.hasActiveSupply(board, targets, item);
    }
}
