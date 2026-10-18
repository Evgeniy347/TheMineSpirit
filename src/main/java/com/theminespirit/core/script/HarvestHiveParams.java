package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record HarvestHiveParams(int radius, int count) {
    public HarvestHiveParams {
        if (radius < CatalogLimits.HERD_RADIUS_MIN || radius > CatalogLimits.HERD_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус поиска " + radius + " вне диапазона ["
                    + CatalogLimits.HERD_RADIUS_MIN + ", " + CatalogLimits.HERD_RADIUS_MAX
                    + "]: дальше загона ульев не ищут");
        }
        if (count < CatalogLimits.HERD_COUNT_MIN || count > CatalogLimits.HERD_COUNT_MAX) {
            throw new IllegalArgumentException("Число ульев " + count + " вне диапазона ["
                    + CatalogLimits.HERD_COUNT_MIN + ", " + CatalogLimits.HERD_COUNT_MAX
                    + "]: за один запуск обходят ограниченное число ульев");
        }
    }

    public static HarvestHiveParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new HarvestHiveParams(
                ScriptArgs.requireInt(params, "radius"),
                ScriptArgs.requireInt(params, "count"));
    }
}
