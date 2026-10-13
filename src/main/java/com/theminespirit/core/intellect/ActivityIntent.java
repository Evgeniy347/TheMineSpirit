package com.theminespirit.core.intellect;

import com.theminespirit.core.coordinator.WorkKind;
import java.util.Map;
import java.util.Optional;

public record ActivityIntent(ActivityId id, Optional<String> script, Map<String, Object> params,
        Optional<WorkKind> workKind) {
    public ActivityIntent {
        if (id == null) {
            throw new IllegalArgumentException("Намерение без деятельности не исполнить: id входит в план духа");
        }
        if (script == null) {
            throw new IllegalArgumentException(
                    "Намерение " + id + " без script-контейнера не исполнить: передайте Optional.empty()");
        }
        if (script.isPresent() && script.get().isBlank()) {
            throw new IllegalArgumentException("Намерение " + id + " с пустой строкой скрипта не исполнить: "
                    + "либо имя скрипта, либо Optional.empty() для деятельности координатора");
        }
        if (params == null) {
            throw new IllegalArgumentException(
                    "Намерение " + id + " без карты параметров не исполнить: пустой набор задают пустой картой");
        }
        params = Map.copyOf(params);
        if (workKind == null) {
            throw new IllegalArgumentException(
                    "Намерение " + id + " без workKind-контейнера не исполнить: передайте Optional.empty()");
        }
    }
}
