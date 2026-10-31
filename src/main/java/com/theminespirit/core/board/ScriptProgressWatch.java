package com.theminespirit.core.board;

import com.theminespirit.core.engine.RunProgress;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class ScriptProgressWatch {
    private record Mark(String key, long since) {}

    private final Map<UUID, Mark> marks = new HashMap<>();

    public boolean advancing(UUID spirit, @Nullable RunProgress progress, long now) {
        Objects.requireNonNull(spirit, "Дух не может быть null: прогресс считается на конкретного духа");
        if (progress == null) {
            marks.remove(spirit);
            return false;
        }
        String key = progress.step() + "/" + progress.currentScript() + "/" + progress.done() + "/" + progress.target();
        Mark last = marks.get(spirit);
        if (last == null || !last.key().equals(key)) {
            marks.put(spirit, new Mark(key, now));
            return true;
        }
        return now - last.since() < TaskStall.STALL_TICKS;
    }
}
