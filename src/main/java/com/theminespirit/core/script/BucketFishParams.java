package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record BucketFishParams(int count, int radius) {
    public BucketFishParams {
        if (count < CatalogLimits.COUNT_MIN || count > CatalogLimits.COUNT_MAX) {
            throw new IllegalArgumentException("count=" + count + " вне диапазона ["
                    + CatalogLimits.COUNT_MIN + ", " + CatalogLimits.COUNT_MAX
                    + "]: число ведёрной рыбы за заход должно лежать в этом промежутке");
        }
        if (radius < CatalogLimits.FISH_RADIUS_MIN || radius > CatalogLimits.FISH_RADIUS_MAX) {
            throw new IllegalArgumentException("radius=" + radius + " вне диапазона ["
                    + CatalogLimits.FISH_RADIUS_MIN + ", " + CatalogLimits.FISH_RADIUS_MAX
                    + "]: радиус поиска рыбы для ведра должен лежать в этом промежутке");
        }
    }

    public static BucketFishParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new BucketFishParams(
                ScriptArgs.optionalInt(params, "count").orElse(CatalogLimits.FISH_COUNT_DEFAULT),
                ScriptArgs.optionalInt(params, "radius").orElse(CatalogLimits.FISH_RADIUS_DEFAULT));
    }
}
