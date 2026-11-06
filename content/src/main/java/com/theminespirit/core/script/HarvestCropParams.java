package com.theminespirit.core.script;

import com.theminespirit.core.farming.CropKind;
import java.util.Map;
import java.util.Objects;

public record HarvestCropParams(CropKind crop, int radius, boolean replant) {
    public HarvestCropParams {
        Objects.requireNonNull(crop, "Вид культуры не может быть null: «любой вид» задают CropKind.ANY");
        if (radius < CatalogLimits.FARM_RADIUS_MIN || radius > CatalogLimits.FARM_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус поиска " + radius + " вне диапазона ["
                    + CatalogLimits.FARM_RADIUS_MIN + ", " + CatalogLimits.FARM_RADIUS_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
    }

    public static HarvestCropParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new HarvestCropParams(
                CropKind.parse(ScriptArgs.requireString(params, "crop")),
                ScriptArgs.requireInt(params, "radius"),
                ScriptArgs.requireBoolean(params, "replant"));
    }
}
