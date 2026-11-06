package com.theminespirit.core.tactics;

import java.util.Set;

public record ModeTransition(String id, Set<CombatMode> from, CombatMode to, Condition when) {
    public ModeTransition {
        from = Set.copyOf(from);
    }
}
