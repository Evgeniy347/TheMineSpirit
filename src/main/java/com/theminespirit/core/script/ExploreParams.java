package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record ExploreParams(int radius, int budget) {
    public ExploreParams {
        if (radius < CatalogLimits.NAV_RADIUS_MIN || radius > CatalogLimits.NAV_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус разведки " + radius + " вне диапазона ["
                    + CatalogLimits.NAV_RADIUS_MIN + ", " + CatalogLimits.NAV_RADIUS_MAX
                    + "]: больший обход кладёт серверный тик");
        }
        if (budget < CatalogLimits.EXPLORE_BUDGET_MIN || budget > CatalogLimits.EXPLORE_BUDGET_MAX) {
            throw new IllegalArgumentException("Бюджет разведки " + budget + " вне диапазона ["
                    + CatalogLimits.EXPLORE_BUDGET_MIN + ", " + CatalogLimits.EXPLORE_BUDGET_MAX
                    + "]: бюджет считает точки обхода, а не время");
        }
    }

    public static ExploreParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new ExploreParams(
                ScriptArgs.optionalInt(params, "radius").orElse(CatalogLimits.NAV_RADIUS_DEFAULT),
                ScriptArgs.optionalInt(params, "budget").orElse(CatalogLimits.EXPLORE_BUDGET_DEFAULT));
    }
}
