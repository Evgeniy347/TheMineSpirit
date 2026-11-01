package com.theminespirit.core.route;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class RouteFailures {
    public record Key(UUID spirit, BlockCoord from, BlockCoord target, int radius, int blocks) {
        public Key {
            Objects.requireNonNull(spirit, "Дух не может быть null: без него кэш не привязать к запросу");
            Objects.requireNonNull(from, "Старт не может быть null: от него зависит расчёт маршрута");
            Objects.requireNonNull(target, "Цель не может быть null: от неё зависит расчёт маршрута");
        }
    }

    private static final int MAX_ENTRIES = 512;

    private final Map<Key, Long> failed = new HashMap<>();

    public boolean recentlyFailed(Key key, long now, long ttlTicks) {
        Objects.requireNonNull(key, "Ключ запроса не может быть null: без него нечего искать в кэше");
        if (ttlTicks < 1) {
            throw new IllegalArgumentException("Срок кэша " + ttlTicks + " тиков не положителен: кэш должен истекать");
        }
        Long at = failed.get(key);
        return at != null && now >= at && now - at < ttlTicks;
    }

    public void remember(Key key, long now) {
        Objects.requireNonNull(key, "Ключ запроса не может быть null: без него нечего запомнить");
        if (failed.size() >= MAX_ENTRIES) {
            failed.clear();
        }
        failed.put(key, now);
    }

    public void forget(UUID spirit) {
        Objects.requireNonNull(spirit, "Дух не может быть null: иначе неясно, чьи записи забывать");
        failed.keySet().removeIf(key -> key.spirit().equals(spirit));
    }

    public int size() {
        return failed.size();
    }
}
