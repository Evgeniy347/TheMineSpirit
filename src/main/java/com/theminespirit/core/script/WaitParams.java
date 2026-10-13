package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record WaitParams(int ticks) {
    public WaitParams {
        if (ticks < CatalogLimits.WAIT_TICKS_MIN || ticks > CatalogLimits.WAIT_TICKS_MAX) {
            throw new IllegalArgumentException("Число тиков ожидания " + ticks + " вне диапазона ["
                    + CatalogLimits.WAIT_TICKS_MIN + ", " + CatalogLimits.WAIT_TICKS_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
    }

    public static WaitParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new WaitParams(ScriptArgs.requireInt(params, "ticks"));
    }
}
