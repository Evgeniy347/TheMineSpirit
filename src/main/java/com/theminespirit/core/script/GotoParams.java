package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record GotoParams(int x, int y, int z) {
    public static GotoParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new GotoParams(
                ScriptArgs.requireInt(params, "x"),
                ScriptArgs.requireInt(params, "y"),
                ScriptArgs.requireInt(params, "z"));
    }
}