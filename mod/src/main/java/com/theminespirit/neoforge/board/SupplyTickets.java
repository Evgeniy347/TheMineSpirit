package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.StateChangeLog;
import com.theminespirit.core.board.SupplyGate;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.inventory.StorageFinder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class SupplyTickets {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final StateChangeLog CHANGES = new StateChangeLog();

    private SupplyTickets() {}

    static void issue(
            ServerLevel level,
            BoardService service,
            Map<String, TicketTarget> targets,
            @Nullable BlockCoord home,
            SupplyGate.Request request,
            BoardCase boardCase,
            long now) {
        String id = (boardCase == BoardCase.GATHER_MATERIALS ? "supply_gather:" : "supply:") + request.item();
        Optional<TicketTarget> centre = targets.values().stream()
                .filter(candidate -> request.item().equals(candidate.supplyItem()))
                .findFirst();
        TicketTarget target;
        if (centre.isPresent()) {
            target = centre.orElseThrow();
        } else if (home != null) {
            target = TicketTarget.at(home);
        } else {
            return;
        }
        Optional<BlockCoord> point =
                StorageFinder.depositPoint(level, target.target(), request.item(), request.count());
        if (point.isPresent()) {
            CHANGES.forget("supply:" + id);
            target = target.withSupply(request.item(), request.count()).withDeposit(point.orElseThrow());
        } else {
            if (CHANGES.changed("supply:" + id, "no-container")) {
                LOGGER.info(
                        "[supply] {}: на складе поселения нет контейнера под '{}' — доска сундуков ставит "
                                + "сундук двора; добыча идёт без точки сдачи, сдача — ближайшему сундуку",
                        id,
                        request.item());
            }
            if (SupplyGate.needsDepositPoint(boardCase)) {
                return;
            }
            target = target.withSupply(request.item(), request.count());
        }
        service.submit(
                new BoardTicket(
                        id,
                        boardCase,
                        Optional.empty(),
                        BoardRules.priorityOf(boardCase),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                target);
    }
}
