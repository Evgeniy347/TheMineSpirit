package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record DeliverParams(String item, int count, String to) {
    public DeliverParams {
        if (item == null || item.isBlank()) {
            throw new IllegalArgumentException("Не задан предмет deliver: без него нечего доставлять");
        }
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Не задан адресат deliver: пустую строку не сопоставить ни с кем");
        }
        PersonSelector.requirePerson(to, "to");
        if (count < CatalogLimits.COUNT_MIN || count > CatalogLimits.COUNT_MAX) {
            throw new IllegalArgumentException("Число предметов " + count + " вне диапазона ["
                    + CatalogLimits.COUNT_MIN + ", " + CatalogLimits.COUNT_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
    }

    public static DeliverParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new DeliverParams(
                ScriptArgs.requireString(params, "item"),
                ScriptArgs.requireInt(params, "count"),
                ScriptArgs.requireString(params, "to"));
    }
}
