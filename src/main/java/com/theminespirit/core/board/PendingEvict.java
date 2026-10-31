package com.theminespirit.core.board;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

final class PendingEvict {
    private PendingEvict() {}

    static int evict(Map<String, BoardTicket> tickets, Map<String, BoardStatus> statuses, Consumer<String> cancel) {
        Objects.requireNonNull(tickets, "Билеты доски не могут быть null: переполнение считается по ним");
        Objects.requireNonNull(statuses, "Статусы доски не могут быть null: вытесняются только ждущие");
        Objects.requireNonNull(cancel, "Отмена не может быть null: вытесненное задание снимают с доски");
        List<BoardTicket> unissued = tickets.values().stream()
                .filter(ticket -> statuses.get(ticket.id()) == BoardStatus.PENDING
                        || statuses.get(ticket.id()) == BoardStatus.WAITING_RESOURCES)
                .sorted(Comparator.comparingLong(BoardTicket::createdAt).thenComparing(BoardTicket::id))
                .toList();
        int evicted = 0;
        for (int index = 0; index < unissued.size() - BoardTuning.maxPending(); index++) {
            cancel.accept(unissued.get(index).id());
            evicted++;
        }
        return evicted;
    }
}
