package com.theminespirit.core.script;

import com.theminespirit.core.tactics.CombatMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record SetCombatModeParams(CombatMode mode) {
    public static final List<String> MODE_IDS = List.of("passive", "defensive", "protect", "aggressive");

    public SetCombatModeParams {
        Objects.requireNonNull(mode, "Режим боя не может быть null: без него непонятно, что включать духу");
    }

    public static SetCombatModeParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        String value = ScriptArgs.requireString(params, "mode").toLowerCase(Locale.ROOT);
        if (!MODE_IDS.contains(value)) {
            throw new IllegalArgumentException("Режим боя '" + value + "' неизвестен: допустимы " + MODE_IDS
                    + " (guard_area включается зоной охраны, а не режимом)");
        }
        return new SetCombatModeParams(CombatMode.valueOf(value.toUpperCase(Locale.ROOT)));
    }
}
