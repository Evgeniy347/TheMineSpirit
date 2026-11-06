package com.theminespirit.core.script;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.perception.MemoryCall;
import com.theminespirit.core.perception.MemoryType;
import java.util.Map;
import java.util.Objects;

public final class MemoryParams {
    private MemoryParams() {}

    public static MemoryCall remember(Map<String, Object> params) {
        GotoParams point = GotoParams.from(params);
        return new MemoryCall.Remember(
                ScriptArgs.requireString(params, "name"),
                type(params),
                new BlockCoord(point.x(), point.y(), point.z()));
    }

    public static MemoryCall recall(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new MemoryCall.Recall(ScriptArgs.requireString(params, "name"));
    }

    public static MemoryCall recallAll(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new MemoryCall.RecallAll(type(params));
    }

    public static MemoryCall forget(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new MemoryCall.Forget(ScriptArgs.requireString(params, "name"));
    }

    private static MemoryType type(Map<String, Object> params) {
        return MemoryType.parse(ScriptArgs.requireString(params, "type"));
    }
}
