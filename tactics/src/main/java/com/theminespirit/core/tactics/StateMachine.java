package com.theminespirit.core.tactics;

import java.util.List;
import java.util.Optional;

public final class StateMachine {
    private final List<StateTransition> transitions;
    private final RuleCounter counter;

    public StateMachine(List<StateTransition> transitions, RuleCounter counter) {
        this.transitions = List.copyOf(transitions);
        this.counter = counter;
    }

    public StateStep next(TacticalState current, TacticalOption decision, FactMap facts) {
        for (StateTransition transition : transitions) {
            if (!transition.from().contains(current)) {
                continue;
            }
            counter.check();
            if (transition.applies(current, decision, facts)) {
                return new StateStep(transition.to(), Optional.of(transition.id()));
            }
        }
        return new StateStep(current, Optional.empty());
    }
}
