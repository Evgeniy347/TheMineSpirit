package com.theminespirit.core.script;

import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.OverflowMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CollectDropsParams(int radius, IdFilter items, OverflowMode overflow) {
    public CollectDropsParams {
        if (radius < CatalogLimits.DROP_RADIUS_MIN || radius > CatalogLimits.DROP_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус подбора " + radius + " вне диапазона ["
                    + CatalogLimits.DROP_RADIUS_MIN + ", " + CatalogLimits.DROP_RADIUS_MAX
                    + "]: больший радиус гонит духа за предметами через полкарты");
        }
        Objects.requireNonNull(items, "items не может быть null: пустой фильтр задаётся IdFilter.matchAll()");
    }

    public static CollectDropsParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new CollectDropsParams(
                ScriptArgs.requireInt(params, "radius"),
                IdFilter.of(ScriptArgs.optionalStringList(params, "filter").orElse(List.of())),
                overflowOf(params.get("overflow")));
    }

    private static OverflowMode overflowOf(Object value) {
        if (value == null) {
            return OverflowMode.BY_VALUE;
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("Параметр 'overflow' скрипта должен быть строкой 'fail', "
                    + "'by_value' или 'return_to_base', а получено " + value.getClass().getSimpleName()
                    + ": тип параметра обеспечивает валидатор плана до запуска");
        }
        return OverflowMode.parse(text);
    }
}
