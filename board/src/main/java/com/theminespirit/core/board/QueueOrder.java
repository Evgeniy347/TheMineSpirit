package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import org.jspecify.annotations.Nullable;

public final class QueueOrder {
    private QueueOrder() {}

    public static Comparator<BoardTicket> of(Collection<BoardTicket> tickets, Map<String, BoardStatus> statuses) {
        return of(tickets, statuses, 0);
    }

    public static Comparator<BoardTicket> of(
            Collection<BoardTicket> tickets, Map<String, BoardStatus> statuses, long now) {
        Map<String, Integer> effective = inherited(tickets, statuses);
        long starveTicks = BoardTuning.starveTicks();
        Comparator<BoardTicket> starved = Comparator.<BoardTicket>comparingInt(
                        ticket -> starved(ticket, now, starveTicks) ? 1 : 0)
                .reversed();
        return starved.thenComparing(Comparator.<BoardTicket>comparingInt(
                                ticket -> effective.getOrDefault(ticket.id(), ticket.priority()))
                        .reversed())
                .thenComparingLong(BoardTicket::createdAt)
                .thenComparing(BoardTicket::id);
    }

    private static boolean starved(BoardTicket ticket, long now, long starveTicks) {
        return starveTicks > 0 && now - ticket.createdAt() >= starveTicks;
    }

    public static Map<String, Integer> inherited(Collection<BoardTicket> tickets, Map<String, BoardStatus> statuses) {
        Objects.requireNonNull(tickets, "Задания очереди не могут быть null: пустую очередь задают пустым списком");
        Objects.requireNonNull(statuses, "Статусы очереди не могут быть null: по ним видно открытые задания");
        Map<String, Integer> effective = new HashMap<>();
        Map<String, List<String>> dependencies = new HashMap<>();
        for (BoardTicket ticket : tickets) {
            if (open(statuses.get(ticket.id()))) {
                effective.put(ticket.id(), ticket.priority());
            }
        }
        for (BoardTicket ticket : tickets) {
            if (!effective.containsKey(ticket.id())) {
                continue;
            }
            List<String> openDependencies = new ArrayList<>();
            for (String dependency : ticket.dependsOn()) {
                if (effective.containsKey(dependency)) {
                    openDependencies.add(dependency);
                }
            }
            dependencies.put(ticket.id(), openDependencies);
        }
        PriorityQueue<Map.Entry<String, Integer>> queue = new PriorityQueue<>(
                Comparator.comparingInt(Map.Entry<String, Integer>::getValue).reversed());
        for (Map.Entry<String, Integer> entry : effective.entrySet()) {
            queue.add(Map.entry(entry.getKey(), entry.getValue()));
        }
        while (!queue.isEmpty()) {
            Map.Entry<String, Integer> top = queue.poll();
            for (String dependency : dependencies.getOrDefault(top.getKey(), List.of())) {
                Integer known = effective.get(dependency);
                if (known != null && known < top.getValue()) {
                    effective.put(dependency, top.getValue());
                    queue.add(Map.entry(dependency, top.getValue()));
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
