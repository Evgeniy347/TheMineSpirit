package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record FishingParams(int count, int durationTicks, int radius) {
    public FishingParams {
        if (count < CatalogLimits.COUNT_MIN || count > CatalogLimits.COUNT_MAX) {
            throw new IllegalArgumentException("count=" + count + " вне диапазона [" + CatalogLimits.COUNT_MIN + ", "
                    + CatalogLimits.COUNT_MAX + "]: число пойманной рыбы должно лежать в этом промежутке");
        }
        if (durationTicks < CatalogLimits.FISH_DURATION_MIN
                || durationTicks > CatalogLimits.FISH_DURATION_MAX) {
            throw new IllegalArgumentException("duration_ticks=" + durationTicks + " вне диапазона ["
                    + CatalogLimits.FISH_DURATION_MIN + ", " + CatalogLimits.FISH_DURATION_MAX
                    + "]: время ловли должно лежать в этом промежутке");
        }
        if (radius < CatalogLimits.FISH_RADIUS_MIN || radius > CatalogLimits.FISH_RADIUS_MAX) {
            throw new IllegalArgumentException("radius=" + radius + " вне диапазона [" + CatalogLimits.FISH_RADIUS_MIN
                    + ", " + CatalogLimits.FISH_RADIUS_MAX + "]: радиус поиска воды должен лежать в этом промежутке");
        }
    }

    public static FishingParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new FishingParams(
                ScriptArgs.optionalInt(params, "count").orElse(CatalogLimits.FISH_COUNT_DEFAULT),
                ScriptArgs.optionalInt(params, "duration_ticks").orElse(CatalogLimits.FISH_DURATION_DEFAULT),
                ScriptArgs.optionalInt(params, "radius").orElse(CatalogLimits.FISH_RADIUS_DEFAULT));
    }
}
