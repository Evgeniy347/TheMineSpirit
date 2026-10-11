package com.theminespirit.core.tactics;

import java.util.Optional;
import java.util.Set;

public record StateTransition(String id, Set<TacticalState> from, TacticalState to, Optional<TacticalOption> decision,
        Condition when) {
    public StateTransition {
        from = Set.copyOf(from);
    }

    public boolean applies(TacticalState state, TacticalOption chosen, FactMap facts) {
        return from.contains(state) && decision.map(option -> option == chosen).orElse(true) && when.matches(facts);
    }
}
