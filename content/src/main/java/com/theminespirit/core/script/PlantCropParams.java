package com.theminespirit.core.script;

import com.theminespirit.core.farming.CropKind;
import java.util.Map;
import java.util.Objects;

public record PlantCropParams(CropKind crop, int radius, int count) {
    public PlantCropParams {
        Objects.requireNonNull(crop, "Вид культуры не может быть null: без него неизвестно, что сажать");
        if (crop == CropKind.ANY) {
            throw new IllegalArgumentException(
                    "Вид культуры не может быть any: сажают конкретный вид, «что-нибудь» дух не посадит");
        }
        if (radius < CatalogLimits.FARM_RADIUS_MIN || radius > CatalogLimits.FARM_RADIUS_MAX) {
            throw new IllegalArgumentException("Радиус поиска " + radius + " вне диапазона ["
                    + CatalogLimits.FARM_RADIUS_MIN + ", " + CatalogLimits.FARM_RADIUS_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
        if (count < CatalogLimits.COUNT_MIN || count > CatalogLimits.COUNT_MAX) {
            throw new IllegalArgumentException("Число клеток " + count + " вне диапазона ["
                    + CatalogLimits.COUNT_MIN + ", " + CatalogLimits.COUNT_MAX
                    + "]: значение должно лежать в этом промежутке");
        }
    }

    public static PlantCropParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new PlantCropParams(
                CropKind.parse(ScriptArgs.requireString(params, "crop")),
                ScriptArgs.requireInt(params, "radius"),
                ScriptArgs.requireInt(params, "count"));
    }
}
