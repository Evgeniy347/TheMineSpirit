package com.theminespirit.core.board.graph;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class GraphNotes {
    public static final long EVERY_TICKS = 1200;
    public static final long NOTICE_EVERY_TICKS = 6000;

    private record Seen(String text, long at) {}

    private final Map<String, Long> last = new HashMap<>();
    private final Map<String, Seen> notices = new HashMap<>();

    public boolean due(String key, String text, long now) {
        Objects.requireNonNull(key, "Ключ заметки не может быть null: по нему считают «раз в N минут»");
        Objects.requireNonNull(text, "Текст заметки не может быть null: по нему видно, изменилась ли просьба");
        Seen before = notices.get(key);
        if (before != null
                && before.text().equals(text)
                && now >= before.at()
                && now - before.at() < NOTICE_EVERY_TICKS) {
            return false;
        }
        notices.put(key, new Seen(text, now));
        return true;
    }

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
