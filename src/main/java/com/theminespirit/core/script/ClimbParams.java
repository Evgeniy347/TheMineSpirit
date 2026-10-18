package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record ClimbParams(boolean down) {
    public static ClimbParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new ClimbParams(ScriptArgs.optionalBoolean(params, "down", false));
    }
}
