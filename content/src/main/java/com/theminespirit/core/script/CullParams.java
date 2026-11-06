package com.theminespirit.core.script;

import com.theminespirit.core.husbandry.Species;
import java.util.Map;
import java.util.Objects;

public record CullParams(Species species, int keep, int radius) {
    public CullParams {
        Objects.requireNonNull(species, "Вид не может быть null: норму держат внутри одного вида");
        if (keep < CatalogLimits.HERD_KEEP_MIN || keep > CatalogLimits.HERD_KEEP_MAX) {
            throw new IllegalArgumentException("Норма " + keep + " вне диапазона ["
                    + CatalogLimits.HERD_KEEP_MIN + ", " + CatalogLimits.HERD_KEEP_MAX
                    + "]: меньше пары стадо вырождается, больше загона не прокормить");
        }
        if (radius < CatalogLimits.HERD_RADIUS_MIN || radius > CatalogLimits.HERD_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус поиска " + radius + " вне диапазона ["
                    + CatalogLimits.HERD_RADIUS_MIN + ", " + CatalogLimits.HERD_RADIUS_MAX
                    + "]: шире загона лишних не ищут");
        }
    }

    public static CullParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        Species species = Species.parse(ScriptArgs.requireString(params, "species"));
        Object keepValue = params.get("keep");
        int keep = keepValue == null ? defaultKeep(species) : ScriptArgs.requireInt(params, "keep");
        return new CullParams(species, keep, ScriptArgs.requireInt(params, "radius"));
    }

    static int defaultKeep(Species species) {
        return species == Species.CHICKEN
                ? CatalogLimits.HERD_KEEP_DEFAULT_CHICKEN
                : CatalogLimits.HERD_KEEP_DEFAULT_CATTLE;
    }
}
