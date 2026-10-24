package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

// place — поставить скрафченную станцию (печь) рядом, если такой поблизости нет: иначе печь оставалась в инвентаре
// крафтера, и плавка других духов падала «нужна печь: рядом нет, в инвентаре нет» (приёмка «джунгли» 24.10).
public record CraftParams(String item, int count, int radius, boolean place) {
    public CraftParams(String item, int count, int radius) {
        this(item, count, radius, false);
    }

    public CraftParams {
        if (item == null || item.isBlank()) {
            throw new IllegalArgumentException("Не задан предмет для craft: без него скрипт не знает, что крафтить");
        }
        if (count < CatalogLimits.COUNT_MIN || count > CatalogLimits.COUNT_MAX) {
            throw new IllegalArgumentException("Число предметов " + count + " вне диапазона ["
                    + CatalogLimits.COUNT_MIN + ", " + CatalogLimits.COUNT_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
        if (radius < CatalogLimits.STATION_RADIUS_MIN || radius > CatalogLimits.STATION_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус поиска станции " + radius + " вне диапазона ["
                    + CatalogLimits.STATION_RADIUS_MIN + ", " + CatalogLimits.STATION_RADIUS_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
    }

    public static CraftParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new CraftParams(
                ScriptArgs.requireString(params, "item"),
                ScriptArgs.requireInt(params, "count"),
                ScriptArgs.requireInt(params, "radius"),
                ScriptArgs.optionalBoolean(params, "place", false));
    }
}
