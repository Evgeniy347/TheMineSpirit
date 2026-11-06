package com.theminespirit.core.script;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Map;

public final class LookAtParams {
    private LookAtParams() {}

    public static BlockCoord target(Map<String, Object> params) {
        GotoParams point = GotoParams.from(params);
        return new BlockCoord(point.x(), point.y(), point.z());
    }
}
