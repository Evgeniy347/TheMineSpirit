package com.theminespirit.core.script;

import com.theminespirit.core.mining.OreKind;
import java.util.Map;
import java.util.Objects;

public record MineVeinParams(OreKind ore, int count, int radius) {
    public MineVeinParams {
        Objects.requireNonNull(ore, "Вид руды не может быть null: без него неизвестно, какую жилу искать");
        if (count < CatalogLimits.COUNT_MIN || count > CatalogLimits.COUNT_MAX) {
            throw new IllegalArgumentException("Число блоков " + count + " вне диапазона ["
                    + CatalogLimits.COUNT_MIN + ", " + CatalogLimits.COUNT_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
        if (radius < CatalogLimits.HARVEST_RADIUS_MIN || radius > CatalogLimits.HARVEST_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус поиска " + radius + " вне диапазона ["
                    + CatalogLimits.HARVEST_RADIUS_MIN + ", " + CatalogLimits.HARVEST_RADIUS_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
    }

    public static MineVeinParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new MineVeinParams(
                ScriptArgs.optionalString(params, "ore").map(OreKind::parse).orElse(OreKind.ANY),
                ScriptArgs.requireInt(params, "count"),
                ScriptArgs.requireInt(params, "radius"));
    }
}
