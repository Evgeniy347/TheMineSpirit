package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record DropToPlayerParams(String item, int count, String to) {
    public DropToPlayerParams {
        if (item == null || item.isBlank()) {
            throw new IllegalArgumentException("Не задан предмет drop_to_player: без него нечего бросать");
        }
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Не задан адресат drop_to_player: пустую строку не сопоставить ни с кем");
        }
        PersonSelector.requirePerson(to, "to");
        if (count < CatalogLimits.COUNT_MIN || count > CatalogLimits.COUNT_MAX) {
            throw new IllegalArgumentException("Число предметов " + count + " вне диапазона ["
                    + CatalogLimits.COUNT_MIN + ", " + CatalogLimits.COUNT_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
    }

    public static DropToPlayerParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new DropToPlayerParams(
                ScriptArgs.requireString(params, "item"),
                ScriptArgs.requireInt(params, "count"),
                ScriptArgs.requireString(params, "to"));
    }
}
