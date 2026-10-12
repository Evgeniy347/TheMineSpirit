package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record SortChestParams(ContainerTarget container, int radius) {
    public SortChestParams {
        Objects.requireNonNull(container, "Контейнер не может быть null: 'nearest' или координаты x,y,z");
        if (radius < CatalogLimits.CONTAINER_RADIUS_MIN || radius > CatalogLimits.CONTAINER_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус поиска контейнера " + radius + " вне диапазона ["
                    + CatalogLimits.CONTAINER_RADIUS_MIN + ", " + CatalogLimits.CONTAINER_RADIUS_MAX
                    + "]: дальше дух уходит из базовой зоны");
        }
    }

    public static SortChestParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new SortChestParams(
                ContainerTarget.parse(ScriptArgs.requireString(params, "container")),
                ScriptArgs.requireInt(params, "radius"));
    }
}
