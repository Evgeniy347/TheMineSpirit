package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record EquipParams(String item) {
    public EquipParams {
        if (item == null || item.isBlank()) {
            throw new IllegalArgumentException(
                    "Не задан предмет equip: без id или шаблона ('*_pickaxe') " + "не понять, что надевать");
        }
    }

    public static EquipParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new EquipParams(ScriptArgs.requireString(params, "item"));
    }
}
