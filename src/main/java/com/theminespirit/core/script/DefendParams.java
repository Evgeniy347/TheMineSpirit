package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record DefendParams(String target) {
    public DefendParams {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("Не задана цель defend: защитить можно только конкретного подопечного, "
                    + "пустую строку не сопоставить ни с кем");
        }
    }

    public static DefendParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new DefendParams(ScriptArgs.requireString(params, "target"));
    }
}
