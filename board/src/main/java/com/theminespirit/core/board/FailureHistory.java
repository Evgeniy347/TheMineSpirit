package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class FailureHistory {
    private static final int KEEP = 3;

    private final Map<String, List<String>> byTicket = new HashMap<>();

    public void note(String ticketId, long tick, @Nullable String reason) {
        Objects.requireNonNull(ticketId, "id задания не может быть null: история пишется на задание");
        List<String> notes = byTicket.computeIfAbsent(ticketId, ignored -> new ArrayList<>());
        notes.add("тик " + tick + ": " + (reason == null || reason.isBlank() ? "причина не записана" : reason));
        while (notes.size() > KEEP) {
            notes.remove(0);
        }
    }

    public List<String> of(String ticketId) {
        Objects.requireNonNull(ticketId, "id задания не может быть null: история читается по заданию");
        return List.copyOf(byTicket.getOrDefault(ticketId, List.of()));
    }
}
