package com.theminespirit.neoforge.rest;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public final class TickEntityCache<T> {
    private final Map<UUID, Optional<T>> values = new HashMap<>();
    private long tick = Long.MIN_VALUE;

    public Optional<T> get(UUID id, long now, Function<UUID, T> load) {
        Objects.requireNonNull(id, "id сущности не может быть null: кэш адресный");
        Objects.requireNonNull(load, "Загрузчик сущности не может быть null: промах кэша читает мир");
        if (now != tick) {
            values.clear();
            tick = now;
        }
        Optional<T> found = values.get(id);
        if (found == null) {
            found = Optional.ofNullable(load.apply(id));
            values.put(id, found);
        }
        return found;
    }

    public void invalidate() {
        values.clear();
        tick = Long.MIN_VALUE;
    }

    public int size() {
        return values.size();
    }
}
