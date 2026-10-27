package com.theminespirit.core.board;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class QueueOrder {
    private QueueOrder() {}

    public static Comparator<BoardTicket> of(Collection<BoardTicket> tickets, Map<String, BoardStatus> statuses) {
        Map<String, Integer> effective = inherited(tickets, statuses);
        return Comparator.<BoardTicket>comparingInt(ticket -> effective.getOrDefault(ticket.id(), ticket.priority()))
                .reversed()
                .thenComparingLong(BoardTicket::createdAt)
                .thenComparing(BoardTicket::id);
    }

    public static Map<String, Integer> inherited(Collection<BoardTicket> tickets, Map<String, BoardStatus> statuses) {
        Objects.requireNonNull(tickets, "Задания очереди не могут быть null: пустую очередь задают пустым списком");
        Objects.requireNonNull(statuses, "Статусы очереди не могут быть null: по ним видно открытые задания");
        Map<String, Integer> effective = new HashMap<>();
        for (BoardTicket ticket : tickets) {
            if (open(statuses.get(ticket.id()))) {
                effective.put(ticket.id(), ticket.priority());
            }
        }
        boolean changed = true;
        for (int pass = 0; changed && pass <= tickets.size(); pass++) {
            changed = false;
            for (BoardTicket ticket : tickets) {
                Integer own = effective.get(ticket.id());
                if (own == null) {
                    continue;
                }
                for (String dependency : ticket.dependsOn()) {
                    Integer before = effective.get(dependency);
                    if (before != null && before < own) {
                        effective.put(dependency, own);
                        changed = true;
                    }
                }
            }
        }
        return Map.copyOf(effective);
    }

    private static boolean open(@Nullable BoardStatus status) {
        return status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED
                || status == BoardStatus.BLOCKED;
    }
}
