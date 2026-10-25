package com.theminespirit.core.script;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record AcquireParams(
        String item, int count, boolean fresh, @Nullable BlockCoord gatherAt) {
    public AcquireParams(String item, int count) {
        this(item, count, false, null);
    }

    public AcquireParams(String item, int count, boolean fresh) {
        this(item, count, fresh, null);
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
                ScriptArgs.optionalBoolean(params, "fresh", false),
                null);
    }

    public static AcquireParams fromPlace(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new AcquireParams(
                ScriptArgs.requireString(params, "item"),
                ScriptArgs.requireInt(params, "count"),
                false,
                new BlockCoord(
                        ScriptArgs.requireInt(params, "x"),
                        ScriptArgs.requireInt(params, "y"),
                        ScriptArgs.requireInt(params, "z")));
    }
}
