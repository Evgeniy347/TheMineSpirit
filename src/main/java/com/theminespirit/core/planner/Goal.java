package com.theminespirit.core.planner;

import java.util.Map;
import java.util.Objects;

public record Goal(String text, Map<String, Object> params) {
    public Goal {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Текст цели пуст: по тексту планировщик выбирает сценарий выполнения");
        }
        params = Map.copyOf(Objects.requireNonNull(params,
                "params не может быть null: пустой набор параметров задают пустой картой"));
    }
}