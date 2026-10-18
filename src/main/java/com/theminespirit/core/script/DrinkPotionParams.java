package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record DrinkPotionParams(String type) {
    public DrinkPotionParams {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Не задан тип зелья drink_potion: без него неизвестно, что пить");
        }
    }

    public static DrinkPotionParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new DrinkPotionParams(ScriptArgs.requireString(params, "type"));
    }
}
