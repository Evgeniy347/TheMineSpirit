package com.theminespirit.core.tactics;

import java.util.Optional;
import java.util.Set;

public record HardRule(String id, int priority, Condition when, HardEffect effect) {
    public Optional<TacticalOption> decision() {
        return effect instanceof HardEffect.Decide decide ? Optional.of(decide.option()) : Optional.empty();
    }

    public Set<TacticalOption> excludes() {
        return effect instanceof HardEffect.Exclude exclude ? exclude.options() : Set.of();
    }
}
