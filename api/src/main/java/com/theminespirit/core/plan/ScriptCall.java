package com.theminespirit.core.plan;

import java.util.Map;
import java.util.Objects;

public record ScriptCall(String script, Map<String, Object> params) implements TaskNode {
    public ScriptCall {
        if (script == null || script.isBlank()) {
            throw new IllegalArgumentException("Имя скрипта в вызове пусто: по нему каталог находит реализацию");
        }
        params = Map.copyOf(
                Objects.requireNonNull(params, "params не может быть null: пустой набор задают пустой картой"));
    }
}
