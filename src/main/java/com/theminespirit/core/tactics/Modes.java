package com.theminespirit.core.tactics;

import java.util.EnumMap;
import java.util.Map;

public record Modes(Map<CombatMode, ModeParams> params, double returnTolerance) {
    public Modes {
        FactChecks.range("Modes.returnTolerance", returnTolerance, 0, 64);
        EnumMap<CombatMode, ModeParams> copy = new EnumMap<>(CombatMode.class);
        copy.putAll(params);
        for (CombatMode mode : CombatMode.values()) {
            if (!copy.containsKey(mode)) {
                throw new IllegalArgumentException(
                        "Modes.params: нет режима " + mode.id() + ": опишите все режимы боя");
            }
        }
        params = Map.copyOf(copy);
    }

    public ModeParams of(CombatMode mode) {
        return params.get(mode);
    }
}
