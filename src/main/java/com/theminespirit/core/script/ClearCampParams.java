package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record ClearCampParams() {
    public static ClearCampParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new ClearCampParams();
    }
}
