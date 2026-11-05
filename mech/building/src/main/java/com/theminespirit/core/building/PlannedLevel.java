package com.theminespirit.core.building;

import java.util.Objects;
import java.util.Optional;

public record PlannedLevel(int level, String name, Optional<String> requires) {
    public PlannedLevel {
        if (level < 1) {
            throw new IllegalArgumentException(
                    "Уровень запланированной постройки " + level + " меньше 1: " + "уровни начинаются с 1");
        }
        Objects.requireNonNull(name, "Название запланированной постройки не может быть null: без него её не описать");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Название запланированной постройки пусто: без него её не описать");
        }
        Objects.requireNonNull(
                requires, "Поле requires не может быть null: уровень без требования задают " + "Optional.empty()");
        if (requires.isPresent() && requires.get().isBlank()) {
            throw new IllegalArgumentException("Требование requires пусто: пустой id не найдёт предыдущий уровень");
        }
    }
}
