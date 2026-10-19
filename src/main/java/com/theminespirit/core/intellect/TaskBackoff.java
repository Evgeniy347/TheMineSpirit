package com.theminespirit.core.intellect;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class TaskBackoff {
    private record Mark(long failTick) {}

    private record BackoffKey(UUID spirit, String script) {}

    private final Map<BackoffKey, Mark> marks = new LinkedHashMap<>();

    public void noteFailure(UUID spirit, String script, long now) {
        if (spirit == null) {
            throw new IllegalArgumentException("Откат без духа не записать: провал принадлежит конкретному духу");
        }
        if (script == null || script.isBlank()) {
            throw new IllegalArgumentException(
                    "Откат без скрипта не записать: проваливается конкретная задача, а не дух целиком");
        }
        if (now < 0) {
            throw new IllegalArgumentException("Откат в тике " + now + ": время не может быть отрицательным");
        }
        prune(now);
        marks.put(new BackoffKey(spirit, script), new Mark(now));
    }

    public boolean blocked(UUID spirit, String script, long now) {
        if (spirit == null) {
            throw new IllegalArgumentException("Откат некого спросить: дух обязан быть указан");
        }
        if (script == null || script.isBlank()) {
            throw new IllegalArgumentException(
                    "Откат нечего спросить: проверяется конкретный скрипт, а не дух целиком");
        }
        if (now < 0) {
            throw new IllegalArgumentException("Откат в тике " + now + ": время не может быть отрицательным");
        }
        Mark mark = marks.get(new BackoffKey(spirit, script));
        return mark != null
                && now - mark.failTick() < IntellectTuning.loadDefault().failBackoffTicks();
    }

    public int size() {
        return marks.size();
    }

    private void prune(long now) {
        long backoff = IntellectTuning.loadDefault().failBackoffTicks();
        Iterator<Map.Entry<BackoffKey, Mark>> entries = marks.entrySet().iterator();
        while (entries.hasNext()) {
            if (now - entries.next().getValue().failTick() >= backoff) {
                entries.remove();
            }
        }
    }
}
