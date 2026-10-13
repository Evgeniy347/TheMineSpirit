package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record AttackParams(String target) {
    public static final String DEFAULT_TARGET = "hostile";

    public AttackParams {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("Не задана цель attack: пустую строку не разобрать ни в один вид цели, "
                    + "а без цели тактика не знает, на кого наступать");
        }
    }

    public static AttackParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new AttackParams(ScriptArgs.optionalString(params, "target", DEFAULT_TARGET));
    }
}
