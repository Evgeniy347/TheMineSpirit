package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.FactChecks;
import com.theminespirit.core.tactics.TacticalOption;
import java.util.Objects;
import java.util.Optional;

public record DecisionSnapshot(TacticalOption decision, CombatMode mode, Optional<String> target, DecisionOrder order) {
    public static final String DECISION = "decision";
    public static final String MODE = "mode";
    public static final String TARGET = "target";
    public static final String ORDER = "order";

    public DecisionSnapshot {
        FactChecks.required("DecisionSnapshot.decision", decision);
        FactChecks.required("DecisionSnapshot.mode", mode);
        FactChecks.required("DecisionSnapshot.target", target);
        FactChecks.required("DecisionSnapshot.order", order);
    }

    public Optional<String> changedField(DecisionSnapshot next) {
        if (decision != next.decision()) {
            return Optional.of(DECISION);
        }
        if (mode != next.mode()) {
            return Optional.of(MODE);
        }
        if (!target.equals(next.target())) {
            return Optional.of(TARGET);
        }
        if (!order.equals(next.order())) {
            return Optional.of(ORDER);
        }
        return Optional.empty();
    }

    public String value(String field) {
        return switch (Objects.requireNonNull(field, "Поле снимка не может быть null")) {
            case DECISION -> decision.name();
            case MODE -> mode.name();
            case TARGET -> target.orElse("нет");
            case ORDER -> order.label();
            default ->
                throw new IllegalArgumentException("Поле снимка '" + field + "' неизвестно: сравниваются " + DECISION
                        + ", " + MODE + ", " + TARGET + " и " + ORDER);
        };
    }
}
