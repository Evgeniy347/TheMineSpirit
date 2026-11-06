package com.theminespirit.core.script;

import com.theminespirit.core.mining.DescentData;
import com.theminespirit.core.mining.DescentDirection;
import com.theminespirit.core.mining.DescentPlan;
import java.util.Map;
import java.util.Objects;

public record DigDescentParams(DescentDirection direction, int y) {
    public DigDescentParams {
        Objects.requireNonNull(direction, "Направление спуска не может быть null: без него неизвестно, куда копать");
        if (y < DescentPlan.WORLD_MIN_Y || y > DescentPlan.WORLD_MAX_Y) {
            throw new IllegalArgumentException("Целевой этаж " + y + " вне мира [" + DescentPlan.WORLD_MIN_Y + ", "
                    + DescentPlan.WORLD_MAX_Y + "]: под дном мира копать некуда, а выше потолка сверлиться некуда");
        }
    }

    public static DigDescentParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new DigDescentParams(
                DescentDirection.parse(ScriptArgs.requireString(params, "direction")),
                ScriptArgs.optionalInt(params, "y")
                        .orElse(DescentData.loadDefault().targetY()));
    }
}
