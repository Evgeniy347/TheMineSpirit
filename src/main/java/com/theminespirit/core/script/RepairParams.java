package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record RepairParams(String item) {
    public RepairParams {
        if (item == null || item.isBlank()) {
            throw new IllegalArgumentException("Не задан предмет repair: без id или шаблона ('*_pickaxe') "
                    + "не понять, что чинить");
        }
    }

    public static RepairParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new RepairParams(ScriptArgs.requireString(params, "item"));
    }
}
