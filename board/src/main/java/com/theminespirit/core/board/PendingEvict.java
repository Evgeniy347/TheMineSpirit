package com.theminespirit.core.board;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

final class PendingEvict {
    private PendingEvict() {}

    static int evict(Map<String, BoardTicket> tickets, Map<String, BoardStatus> statuses, Consumer<String> cancel) {
        return evict(tickets, statuses, cancel, Set.of());
    }

    static int evict(
            Map<String, BoardTicket> tickets,
            Map<String, BoardStatus> statuses,
            Consumer<String> cancel,
            Set<String> liveCauses) {
        Objects.requireNonNull(tickets, "Билеты доски не могут быть null: переполнение считается по ним");
        Objects.requireNonNull(statuses, "Статусы доски не могут быть null: вытесняются только ждущие");
        Objects.requireNonNull(cancel, "Отмена не может быть null: вытесненное задание снимают с доски");
        Objects.requireNonNull(liveCauses, "Живые причины не могут быть null: пустой набор задают пустым");
        List<BoardTicket> pending = tickets.values().stream()
                .filter(ticket -> statuses.get(ticket.id()) == BoardStatus.PENDING
                        || statuses.get(ticket.id()) == BoardStatus.WAITING_RESOURCES)
                .toList();
        int over = pending.size() - BoardTuning.maxPending();
        List<BoardTicket> repairs = pending.stream()
                .filter(ticket -> !liveCauses.contains(ticket.cause()))
                .filter(ticket -> ticket.id().startsWith("repair:"))
                .sorted(Comparator.comparingLong(BoardTicket::createdAt).thenComparing(BoardTicket::id))
                .toList();
        List<BoardTicket> unissued = pending.stream()
                .filter(ticket -> !liveCauses.contains(ticket.cause()))
                .filter(ticket -> !ticket.id().startsWith("repair:"))
                .sorted(Comparator.comparingLong(BoardTicket::createdAt).thenComparing(BoardTicket::id))
                .toList();
        int evicted = 0;
        int repairOver = repairs.size() - BoardTuning.maxRepairPending();
        for (BoardTicket ticket : repairs) {
            if (evicted >= repairOver) {
                break;
            }
            cancel.accept(ticket.id());
            evicted++;
        }
        int rest = Math.max(0, over - evicted);
        for (BoardTicket ticket : unissued) {
            if (rest <= 0) {
                return evicted;
            }
            cancel.accept(ticket.id());
            evicted++;
            rest--;
        }
        return evicted;
    }
}
