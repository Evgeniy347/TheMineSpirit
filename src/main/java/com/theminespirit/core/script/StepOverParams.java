package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record StepOverParams(int dx, int dz) {
    public StepOverParams {
        if (dx < -1 || dx > 1 || dz < -1 || dz > 1) {
            throw new IllegalArgumentException("Шаг (" + dx + ", " + dz + ") не соседняя клетка: "
                    + "step_over шагает только на ±1 по горизонтали");
        }
        if (dx == 0 && dz == 0) {
            throw new IllegalArgumentException("Шаг (0, 0) стоит на месте: step_over шагает на соседнюю клетку");
        }
    }

    public static StepOverParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new StepOverParams(ScriptArgs.requireInt(params, "dx"), ScriptArgs.requireInt(params, "dz"));
    }
}
