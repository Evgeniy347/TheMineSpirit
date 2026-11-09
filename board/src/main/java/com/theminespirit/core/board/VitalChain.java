package com.theminespirit.core.board;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class VitalChain {
    private VitalChain() {}

    public static Map<String, String> withPath(Map<String, String> live, Board board, UUID spirit) {
        Objects.requireNonNull(live, "Живые нужды не могут быть null: у сытого и здорового духа — пустая карта");
        Objects.requireNonNull(board, "Доска не может быть null: на ней ищется путь нужды");
        Objects.requireNonNull(spirit, "Дух не может быть null: путь нужды должен быть доступен ему");
        if (live.isEmpty()) {
            return live;
        }
        Map<String, String> reachable = new LinkedHashMap<>();
        for (Map.Entry<String, String> need : live.entrySet()) {
            Map<String, String> one = Map.of(need.getKey(), need.getValue());
            for (BoardTicket ticket : board.all()) {
                if (leads(ticket, board, spirit) && VitalGate.satisfies(ticket.id(), one)) {
                    reachable.put(need.getKey(), need.getValue());
                    break;
                }
            }
        }
        return reachable;
    }

    private static boolean leads(BoardTicket ticket, Board board, UUID spirit) {
        BoardStatus status = board.statusOf(ticket.id()).orElse(null);
        return (status == BoardStatus.PENDING || status == BoardStatus.ASSIGNED)
                && ticket.boardCase() != BoardCase.ACTIVITY_SENSE
                && ticket.assignee().map(spirit::equals).orElse(true);
    }
}
