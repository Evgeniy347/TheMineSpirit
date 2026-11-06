package com.theminespirit.core.script;

import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.perception.PerceptionQuery;
import java.util.Map;
import java.util.Objects;

public final class PerceptionParams {
    private PerceptionParams() {}

    public static PerceptionQuery blocks(Map<String, Object> params) {
        ScanParams scan = ScanParams.from(params);
        return PerceptionQuery.blocks(scan.filter(), scan.radius());
    }

    public static PerceptionQuery entities(Map<String, Object> params) {
        ScanParams scan = ScanParams.from(params);
        return PerceptionQuery.entities(scan.filter(), scan.radius());
    }

    public static PerceptionQuery hazards(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return PerceptionQuery.hazards(ScanRadius.of(ScriptArgs.requireInt(params, "radius")));
    }
}
