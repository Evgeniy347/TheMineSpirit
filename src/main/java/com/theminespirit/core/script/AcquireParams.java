package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

// fresh — добыть ещё count сверх того, что уже в инвентаре, не беря сам предмет из сундуков: добыча поселения сдаёт
// добытое в сундук, и acquire «иметь» брал оттуда же, запас не рос (приёмка «джунгли» 24.10: gather:jungle_planks
// каждые 100 тиков, хижина ждала 13 досок до конца прогона).
public record AcquireParams(String item, int count, boolean fresh) {
    public AcquireParams(String item, int count) {
        this(item, count, false);
    }

    public AcquireParams {
        if (item == null || item.isBlank()) {
            throw new IllegalArgumentException("Не задан предмет для acquire: без id скрипт не знает, что добыть");
        }
        if (count < CatalogLimits.COUNT_MIN || count > CatalogLimits.COUNT_MAX) {
            throw new IllegalArgumentException("Число предметов " + count + " вне диапазона ["
                    + CatalogLimits.COUNT_MIN + ", " + CatalogLimits.COUNT_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
    }

    public static AcquireParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new AcquireParams(
                ScriptArgs.requireString(params, "item"),
                ScriptArgs.requireInt(params, "count"),
                ScriptArgs.optionalBoolean(params, "fresh", false));
    }
}
