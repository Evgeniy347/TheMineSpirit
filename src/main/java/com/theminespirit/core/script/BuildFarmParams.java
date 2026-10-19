package com.theminespirit.core.script;

import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.farming.FarmPlot;
import java.util.Map;
import java.util.Objects;

public record BuildFarmParams(CropKind crop, FarmPlot plot) {
    public BuildFarmParams {
        Objects.requireNonNull(crop, "Вид культуры не может быть null: без него неизвестно, что сажать");
        Objects.requireNonNull(plot, "Прямоугольник грядки не может быть null: без него неизвестно, где копать");
        if (crop == CropKind.ANY) {
            throw new IllegalArgumentException(
                    "Вид культуры не может быть any: грядку засевают конкретным видом, «что-нибудь» дух не посадит");
        }
        if (crop.hasSupport()) {
            throw new IllegalArgumentException("Культура " + crop.name().toLowerCase()
                    + " растёт на опоре, а build_farm готовит грядку из пашни: для неё нужен plant_crop");
        }
    }

    public static BuildFarmParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        int x1 = ScriptArgs.requireInt(params, "x1");
        int z1 = ScriptArgs.requireInt(params, "z1");
        int x2 = ScriptArgs.requireInt(params, "x2");
        int z2 = ScriptArgs.requireInt(params, "z2");
        BuildingCoords.checkX(x1);
        BuildingCoords.checkZ(z1);
        BuildingCoords.checkX(x2);
        BuildingCoords.checkZ(z2);
        return new BuildFarmParams(
                CropKind.parse(ScriptArgs.requireString(params, "crop")), FarmPlot.of(x1, z1, x2, z2));
    }
}
