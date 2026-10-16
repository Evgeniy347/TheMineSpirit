package com.theminespirit.core.intellect;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class TaskBackoff {
    private record Mark(String script, long failTick) {
    }

    private final Map<UUID, Mark> marks = new LinkedHashMap<>();

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
        marks.put(spirit, new Mark(script, now));
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
        Mark mark = marks.get(spirit);
        return mark != null && mark.script().equals(script) && now - mark.failTick() < IntellectTuning.FAIL_BACKOFF_TICKS;
    }

    public int size() {
        return marks.size();
    }

    private void prune(long now) {
        Iterator<Map.Entry<UUID, Mark>> entries = marks.entrySet().iterator();
        while (entries.hasNext()) {
            if (now - entries.next().getValue().failTick() >= IntellectTuning.FAIL_BACKOFF_TICKS) {
                entries.remove();
            }
        }
    }
}
