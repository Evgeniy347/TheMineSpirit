package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.DemolitionScope;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class DemolitionHistory {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<BoardStatus> OPEN =
            EnumSet.of(BoardStatus.PENDING, BoardStatus.WAITING_RESOURCES, BoardStatus.ASSIGNED);

    private DemolitionHistory() {}

    static boolean busy(BoardService service, long now, DemolitionScope scope) {
        return service.board().all().stream()
                .filter(ticket -> ticket.id().startsWith(PlanDemolition.PREFIX))
                .filter(ticket -> DemolitionScope.of(ticket.cause()) == scope || scope == DemolitionScope.BOTH)
                .anyMatch(ticket ->
                        OPEN.contains(service.board().statusOf(ticket.id()).orElse(BoardStatus.DONE))
                                || now - ticket.createdAt() < PlanDemolition.COOLDOWN);
    }

    static List<BoardTicket> open(BoardService service, DemolitionScope scope) {
        return service.board().all().stream()
                .filter(ticket -> ticket.id().startsWith(PlanDemolition.PREFIX))
                .filter(ticket -> DemolitionScope.of(ticket.cause()) == scope || scope == DemolitionScope.BOTH)
                .filter(ticket ->
                        OPEN.contains(service.board().statusOf(ticket.id()).orElse(BoardStatus.DONE)))
                .toList();
    }

    static void supersede(ServerLevel level, BoardService service, String keep, DemolitionScope scope) {
        for (BoardTicket ticket : List.copyOf(service.board().all())) {
            if (ticket.id().startsWith(PlanDemolition.PREFIX)
                    && !ticket.id().equals(keep)
                    && (DemolitionScope.of(ticket.cause()) == scope || scope == DemolitionScope.BOTH)
                    && service.board().statusOf(ticket.id()).orElse(null) == BoardStatus.BLOCKED
                    && service.cancel(level, ticket.id())) {
                LOGGER.info("[tidy] старый заблокированный {} снят: набор уборки сменился на {}", ticket.id(), keep);
            }
        }
    }

    static String previous(BoardService service, DemolitionScope scope) {
        return service.board().all().stream()
                .filter(ticket -> ticket.id().startsWith(PlanDemolition.PREFIX))
                .filter(ticket -> DemolitionScope.of(ticket.cause()) == scope || scope == DemolitionScope.BOTH)
                .max(Comparator.comparingLong(BoardTicket::createdAt))
                .map(ticket -> ticket.id() + " ("
                        + service.board().statusOf(ticket.id()).orElse(BoardStatus.DONE) + ")")
                .orElse("уборки ещё не было");
    }
}
