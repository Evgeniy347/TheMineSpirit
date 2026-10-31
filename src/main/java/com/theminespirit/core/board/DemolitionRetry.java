package com.theminespirit.core.board;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class DemolitionRetry {
    private static final int MEMORY = 256;

    private final Map<String, Long> retryAt = new HashMap<>();
    private final Map<String, Integer> attempts = new HashMap<>();

    public boolean waiting(String id, long now) {
        Objects.requireNonNull(id, "Набор уборки не может быть null: без него нечего повторять");
        Long at = retryAt.get(id);
        return at != null && now < at;
    }

    public long until(String id) {
        Objects.requireNonNull(id, "Набор уборки не может быть null: без него нечего повторять");
        return retryAt.getOrDefault(id, 0L);
    }

    public String signature(String id) {
        Objects.requireNonNull(id, "Набор уборки не может быть null: без него нечего подписывать");
        return id + "#" + (attempts.getOrDefault(id, 0) + 1);
    }

    public void submitted(String id, long now, long cooldown) {
        Objects.requireNonNull(id, "Набор уборки не может быть null: без него нечего запоминать");
        if (cooldown < 0) {
            throw new IllegalArgumentException("Пауза повтора " + cooldown + " отрицательна: время назад не идёт");
        }
        attempts.merge(id, 1, Integer::sum);
        retryAt.put(id, now + cooldown);
        if (retryAt.size() > MEMORY) {
            retryAt.values().removeIf(at -> now >= at);
        }
    }
}
