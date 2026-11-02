package com.theminespirit.core.board;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class BlockNoteLog {
    private static final String FAILURES = "failures=";
    private static final String HISTORY = "history=";

    private final ChangeSummary log;

    public BlockNoteLog(long window) {
        this.log = new ChangeSummary(window);
    }

    public Optional<ChangeSummary.Summary> observe(BoardLogEntry entry, long now) {
        Objects.requireNonNull(entry, "Запись блокировки не может быть null: по ней решают, печатать ли строку");
        String key = entry.event() + ":" + entry.ticketId();
        return log.note(key, signature(entry), 1, now);
    }

    public void retain(Set<String> ticketIds) {
        Objects.requireNonNull(ticketIds, "Список живых заданий не может быть null: пустой задают Set.of()");
        Set<String> keys = new HashSet<>();
        for (String ticketId : ticketIds) {
            keys.add(BoardLog.Event.BLOCKED + ":" + ticketId);
            keys.add(BoardLog.Event.UNBLOCKED + ":" + ticketId);
        }
        log.retain(keys);
    }

    static String signature(BoardLogEntry entry) {
        List<String> stable = entry.modifiers().stream()
                .filter(modifier -> !modifier.startsWith(FAILURES) && !modifier.startsWith(HISTORY))
                .toList();
        return stable.isEmpty() ? entry.event().name() : String.join("|", stable);
    }
}
