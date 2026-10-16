package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record EatParams(Optional<String> item) {
    public EatParams {
        Objects.requireNonNull(item, "item не может быть null: незаданный предмет задают Optional.empty()");
        if (item.isPresent() && item.get().isBlank()) {
            throw new IllegalArgumentException("Пустое имя еды в eat: назовите предмет или не задавайте поле — "
                    + "тогда дух съест лучшее из инвентаря");
        }
    }

    public static EatParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        String item = ScriptArgs.optionalString(params, "item", "");
        return new EatParams(item.isBlank() ? Optional.empty() : Optional.of(item));
    }
}
