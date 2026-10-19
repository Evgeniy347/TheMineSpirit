package com.theminespirit.core.script;

import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.observation.ScanRadius;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ScanParams(ScanRadius radius, IdFilter filter) {
    public ScanParams {
        Objects.requireNonNull(
                radius, "radius не может быть null: скан без радиуса не знает, сколько мира просматривать");
        Objects.requireNonNull(filter, "filter не может быть null: пустой фильтр задаётся IdFilter.matchAll()");
    }

    public static ScanParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new ScanParams(
                ScanRadius.of(ScriptArgs.requireInt(params, "radius")),
                IdFilter.of(ScriptArgs.optionalStringList(params, "filter").orElse(List.of())));
    }
}
