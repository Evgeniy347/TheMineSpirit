package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record SwapToolParams(String block) {
    public SwapToolParams {
        if (block == null || block.isBlank()) {
            throw new IllegalArgumentException(
                    "Не задан блок swap_tool: без него неизвестно, для чего выбирать инструмент");
        }
    }

    public static SwapToolParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new SwapToolParams(ScriptArgs.requireString(params, "block"));
    }
}
