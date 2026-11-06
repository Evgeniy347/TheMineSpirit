package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record SayParams(String text) {
    public SayParams {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Не задан текст say: пустое сообщение нечего отправлять в чат");
        }
    }

    public static SayParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new SayParams(ScriptArgs.requireString(params, "text"));
    }
}
