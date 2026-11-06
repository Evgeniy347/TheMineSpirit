package com.theminespirit.core.board;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class WaitNotes {
    private record Note(String reason, long since) {}

    private final Map<String, Note> notes = new LinkedHashMap<>();

    void note(String ticketId, @Nullable String reason, long now) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: причина ожидания пишется на задание");
        if (reason == null) {
            notes.remove(ticketId);
        } else {
            notes.put(ticketId, new Note(reason, now));
        }
    }

    Optional<String> of(String ticketId, long now) {
        Objects.requireNonNull(ticketId, "ticketId не может быть null: причина ожидания читается по заданию");
        Note note = notes.get(ticketId);
        if (note == null) {
            return Optional.empty();
        }
        if (now - note.since() >= BoardTuning.boardIntervalTicks()) {
            notes.remove(ticketId);
            return Optional.empty();
        }
        return Optional.of(note.reason());
    }

    void forget(String ticketId) {
        notes.remove(ticketId);
    }

    void retain(Collection<String> ticketIds) {
        notes.keySet().retainAll(ticketIds);
    }
}
