package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record TransferParams(ContainerTarget container, String item, int count, int radius, boolean leftover) {
    public TransferParams {
        Objects.requireNonNull(container, "Контейнер не может быть null: 'nearest' или координаты x,y,z");
        if (item == null || item.isBlank()) {
            throw new IllegalArgumentException(
                    "Не задан предмет переноса: без id или шаблона ('*_ingot') " + "не понять, что класть в контейнер");
        }
        if (count < CatalogLimits.COUNT_MIN || count > CatalogLimits.COUNT_MAX) {
            throw new IllegalArgumentException("Число предметов " + count + " вне диапазона ["
                    + CatalogLimits.COUNT_MIN + ", " + CatalogLimits.COUNT_MAX
                    + "]: перенос одновременно тысяч стопок занял бы серверный тик");
        }
        if (radius < CatalogLimits.CONTAINER_RADIUS_MIN || radius > CatalogLimits.CONTAINER_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус поиска контейнера " + radius + " вне диапазона ["
                    + CatalogLimits.CONTAINER_RADIUS_MIN + ", " + CatalogLimits.CONTAINER_RADIUS_MAX
                    + "]: дальше дух уходит из базовой зоны");
        }
    }

    public static TransferParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new TransferParams(
                ContainerTarget.parse(ScriptArgs.requireString(params, "container")),
                ScriptArgs.requireString(params, "item"),
                ScriptArgs.requireInt(params, "count"),
                ScriptArgs.requireInt(params, "radius"),
                ScriptArgs.optionalBoolean(params, "leftover", false));
    }
}
