package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record PillarUpParams(int height, @Nullable String block, boolean down) {
    public PillarUpParams {
        if (height < 1) {
            throw new IllegalArgumentException("Высота столба " + height + " меньше 1: строить нечего");
        }
    }

    public static PillarUpParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        int height = ScriptArgs.requireInt(params, "height");
        String block = ScriptArgs.optionalString(params, "block", "");
        if (block.isBlank()) {
            block = null;
        }
        boolean down = ScriptArgs.optionalBoolean(params, "down", false);
        return new PillarUpParams(height, block, down);
    }
}
