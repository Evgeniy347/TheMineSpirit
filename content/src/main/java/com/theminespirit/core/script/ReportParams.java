package com.theminespirit.core.script;

import com.theminespirit.core.tactics.TargetSelector;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record ReportParams(Optional<String> to) {
    public ReportParams {
        Objects.requireNonNull(to, "to не может быть null: доклад без адресата задают Optional.empty()");
        if (to.isPresent()) {
            TargetSelector.Target target = TargetSelector.parse(to.get());
            if (target.kind() != TargetSelector.Kind.PLAYER) {
                throw new IllegalArgumentException("Параметр 'to' = '" + to.get() + "' должен указывать на игрока "
                        + "(player:<имя>): доклад идёт в чат, а не духу и не виду цели");
            }
        }
    }

    public static ReportParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        String to = ScriptArgs.optionalString(params, "to", "");
        return new ReportParams(to.isBlank() ? Optional.empty() : Optional.of(to));
    }
}
