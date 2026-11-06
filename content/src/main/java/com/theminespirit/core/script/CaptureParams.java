package com.theminespirit.core.script;

import com.theminespirit.core.husbandry.Species;
import java.util.Map;
import java.util.Objects;

public record CaptureParams(Species species, int radius) {
    public CaptureParams {
        Objects.requireNonNull(species, "Вид не может быть null: кольцо строится под размер животного");
        if (radius < CatalogLimits.CAPTURE_RADIUS_MIN || radius > CatalogLimits.CAPTURE_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус поиска " + radius + " вне диапазона ["
                    + CatalogLimits.CAPTURE_RADIUS_MIN + ", " + CatalogLimits.CAPTURE_RADIUS_MAX
                    + "]: дальше животное не догнать до постройки кольца");
        }
    }

    public static CaptureParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new CaptureParams(
                Species.parse(ScriptArgs.requireString(params, "species")), ScriptArgs.requireInt(params, "radius"));
    }
}
