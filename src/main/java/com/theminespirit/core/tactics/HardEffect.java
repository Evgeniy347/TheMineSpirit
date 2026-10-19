package com.theminespirit.core.tactics;

import java.util.Set;

public sealed interface HardEffect permits HardEffect.Decide, HardEffect.Exclude, HardEffect.NoRoute {
    record Decide(TacticalOption option) implements HardEffect {}

    record Exclude(Set<TacticalOption> options) implements HardEffect {
        public Exclude {
            options = Set.copyOf(options);
        }
    }

    record NoRoute(TacticalOption option) implements HardEffect {}
}
