package com.theminespirit.core.board;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class BoardDependencies {
    private final Map<String, BoardTicket> tickets;
    private final Map<String, BoardStatus> statuses;
    private final BoardLedger ledger;

    BoardDependencies(Map<String, BoardTicket> tickets, Map<String, BoardStatus> statuses, BoardLedger ledger) {
        this.tickets = Objects.requireNonNull(tickets, "Задания доски не могут быть null: у них читают зависимости");
        this.statuses = Objects.requireNonNull(statuses, "Статусы доски не могут быть null: по ним видно готовность");
        this.ledger = Objects.requireNonNull(ledger, "Книга построек не может быть null: по ней готово здание");
    }

    boolean met(BoardTicket ticket) {
        Objects.requireNonNull(ticket, "ticket не может быть null: предшественники считаются у задания");
        for (String dependency : ticket.dependsOn()) {
            Optional<BoardLedger.BuildingMark> mark = BoardLedger.parse(dependency);
            if (mark.isPresent()) {
                if (!buildingDone(mark.orElseThrow())) {
                    return false;
                }
                continue;
            }
            if (statuses.get(dependency) != BoardStatus.DONE) {
                return false;
            }
        }
        return true;
    }

    private boolean buildingDone(BoardLedger.BuildingMark mark) {
        if (ledger.has(mark.boardCase(), mark.level())) {
            return true;
        }
        for (BoardTicket ticket : tickets.values()) {
            if (ticket.boardCase() == mark.boardCase()
                    && ticket.level() == mark.level()
                    && statuses.get(ticket.id()) == BoardStatus.DONE) {
                return true;
            }
        }
        return false;
    }
}
