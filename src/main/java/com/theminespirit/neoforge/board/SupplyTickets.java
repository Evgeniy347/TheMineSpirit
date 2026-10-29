package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.SupplyGate;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.inventory.StorageFinder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class SupplyTickets {
    private static final Logger LOGGER = LogUtils.getLogger();

    private SupplyTickets() {}

    static void issue(
            ServerLevel level,
            BoardService service,
            Map<String, TicketTarget> targets,
            BlockCoord home,
            SupplyGate.Request request,
            BoardCase boardCase,
            long now) {
        String id = (boardCase == BoardCase.GATHER_MATERIALS ? "supply_gather:" : "supply:") + request.item();
        TicketTarget target = targets.values().stream()
                .filter(candidate -> request.item().equals(candidate.supplyItem()))
                .findFirst()
                .orElse(TicketTarget.at(home));
        Optional<BlockCoord> point =
                StorageFinder.depositPoint(level, target.target(), request.item(), request.count());
        if (point.isEmpty()) {
            LOGGER.info(
                    "[supply] {} отложено: у точки {} нет допустимого контейнера под '{}' — нужен сундук двора "
                            + "(его ставит доска сундуков), а не сдача в пустом месте",
                    id,
                    target.target(),
                    request.item());
            return;
        }
        target = target.withSupply(request.item(), request.count()).withDeposit(point.orElseThrow());
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
