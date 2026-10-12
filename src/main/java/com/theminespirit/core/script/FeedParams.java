package com.theminespirit.core.script;

import com.theminespirit.core.husbandry.Species;
import java.util.Map;
import java.util.Objects;

public record FeedParams(Species species, int radius, int count, boolean babiesFirst) {
    public FeedParams {
        Objects.requireNonNull(species, "Вид не может быть null: корм подбирается под вид");
        if (radius < CatalogLimits.HERD_RADIUS_MIN || radius > CatalogLimits.HERD_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус поиска " + radius + " вне диапазона ["
                    + CatalogLimits.HERD_RADIUS_MIN + ", " + CatalogLimits.HERD_RADIUS_MAX
                    + "]: шире загона кормить некого");
        }
        if (count < CatalogLimits.HERD_COUNT_MIN || count > CatalogLimits.HERD_COUNT_MAX) {
            throw new IllegalArgumentException("Число голов " + count + " вне диапазона ["
                    + CatalogLimits.HERD_COUNT_MIN + ", " + CatalogLimits.HERD_COUNT_MAX
                    + "]: за один запуск кормят ограниченное стадо");
        }
    }

    public static FeedParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new FeedParams(
                Species.parse(ScriptArgs.requireString(params, "species")),
                ScriptArgs.requireInt(params, "radius"),
                ScriptArgs.requireInt(params, "count"),
                ScriptArgs.requireBoolean(params, "babiesFirst"));
    }
}
