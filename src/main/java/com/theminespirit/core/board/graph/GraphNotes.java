package com.theminespirit.core.board.graph;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class GraphNotes {
    public static final long EVERY_TICKS = 1200;

    private final Map<String, Long> last = new HashMap<>();

    public boolean due(String key, long now) {
        Objects.requireNonNull(key, "Ключ заметки не может быть null: по нему считают «раз в минуту»");
        Long before = last.get(key);
        if (before != null && now >= before && now - before < EVERY_TICKS) {
            return false;
        }
        last.put(key, now);
        return true;
    }
}
