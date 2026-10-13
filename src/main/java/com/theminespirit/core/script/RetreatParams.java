package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record RetreatParams() {
    public static RetreatParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new RetreatParams();
    }
}
