package com.theminespirit.core.tactics;

import java.util.List;
import java.util.Optional;

public final class ModeMachine {
    private final List<ModeTransition> transitions;
    private final RuleCounter counter;

    public ModeMachine(List<ModeTransition> transitions, RuleCounter counter) {
        this.transitions = List.copyOf(transitions);
        this.counter = counter;
    }

    public ModeStep next(CombatMode current, FactMap facts) {
        for (ModeTransition transition : transitions) {
            if (!transition.from().contains(current)) {
                continue;
            }
            counter.check();
            if (transition.when().matches(facts)) {
                return new ModeStep(transition.to(), Optional.of(transition.id()));
            }
        }
        return new ModeStep(current, Optional.empty());
    }
}
