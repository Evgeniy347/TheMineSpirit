package com.theminespirit.core.board;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class OpenPrefixes {
    private final List<String> prefixes;
    private final Map<String, Integer> counts;

    private OpenPrefixes(List<String> prefixes, Map<String, Integer> counts) {
        this.prefixes = prefixes;
        this.counts = counts;
    }

    public static OpenPrefixes of(Board board, Collection<String> prefixes) {
        Objects.requireNonNull(board, "Счёт открытых заданий без доски не построить: заданий неоткуда взять");
        Objects.requireNonNull(prefixes, "Список префиксов не может быть null: считать нечего");
        List<String> watched = List.copyOf(prefixes);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String prefix : watched) {
            counts.put(prefix, 0);
        }
        for (BoardTicket ticket : board.tickets()) {
            BoardStatus status = board.statusOf(ticket.id()).orElse(null);
            if (status == null || !status.open()) {
                continue;
            }
            for (String prefix : watched) {
                if (ticket.id().startsWith(prefix)) {
                    counts.merge(prefix, 1, Integer::sum);
                }
            }
        }
        return new OpenPrefixes(watched, counts);
    }

    public int open(String prefix) {
        return counts.getOrDefault(prefix, 0);
    }

    public void posted(String ticketId) {
        Objects.requireNonNull(ticketId, "Id поставленного задания не может быть null: счёт не сдвинется");
        for (String prefix : prefixes) {
            if (ticketId.startsWith(prefix)) {
                counts.merge(prefix, 1, Integer::sum);
            }
        }
    }
}
