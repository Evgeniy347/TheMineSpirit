package com.theminespirit.core.board;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class WaitNotes {
    private final Map<String, String> notes = new LinkedHashMap<>();

    void note(String ticketId, @Nullable String reason) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: причина ожидания пишется на задание");
        if (reason == null) {
            notes.remove(ticketId);
        } else {
            notes.put(ticketId, reason);
        }
    }

    Optional<String> of(String ticketId) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: причина ожидания читается по заданию");
        return Optional.ofNullable(notes.get(ticketId));
    }

    void forget(String ticketId) {
        notes.remove(ticketId);
    }

    void retain(Collection<String> ticketIds) {
        notes.keySet().retainAll(ticketIds);
    }
}
