package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record TakeParams(String item, int count, String from) {
    public TakeParams {
        if (item == null || item.isBlank()) {
            throw new IllegalArgumentException("Не задан предмет take: без него нечего брать");
        }
        if (from == null || from.isBlank()) {
            throw new IllegalArgumentException("Не задан источник take: пустую строку не сопоставить ни с кем");
        }
        PersonSelector.requirePerson(from, "from");
        if (count < CatalogLimits.COUNT_MIN || count > CatalogLimits.COUNT_MAX) {
            throw new IllegalArgumentException("Число предметов " + count + " вне диапазона ["
                    + CatalogLimits.COUNT_MIN + ", " + CatalogLimits.COUNT_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
    }

    public static TakeParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new TakeParams(
                ScriptArgs.requireString(params, "item"),
                ScriptArgs.requireInt(params, "count"),
                ScriptArgs.requireString(params, "from"));
    }
}
