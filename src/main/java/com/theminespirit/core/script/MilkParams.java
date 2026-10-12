package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record MilkParams(int radius, int count) {
    public MilkParams {
        if (radius < CatalogLimits.HERD_RADIUS_MIN || radius > CatalogLimits.HERD_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус поиска " + radius + " вне диапазона ["
                    + CatalogLimits.HERD_RADIUS_MIN + ", " + CatalogLimits.HERD_RADIUS_MAX
                    + "]: шире загона доить некого");
        }
        if (count < CatalogLimits.HERD_COUNT_MIN || count > CatalogLimits.HERD_COUNT_MAX) {
            throw new IllegalArgumentException("Число коров " + count + " вне диапазона ["
                    + CatalogLimits.HERD_COUNT_MIN + ", " + CatalogLimits.HERD_COUNT_MAX
                    + "]: за один запуск доят ограниченное стадо");
        }
    }

    public static MilkParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new MilkParams(
                ScriptArgs.requireInt(params, "radius"),
                ScriptArgs.requireInt(params, "count"));
    }
}
