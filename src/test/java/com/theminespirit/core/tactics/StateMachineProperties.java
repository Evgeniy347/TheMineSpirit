package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

class StateMachineProperties {
    private static final TacticsConfig CONFIG = TacticsConfig.loadDefault();

    @Provide
    Arbitrary<Situation> situations() {
        return Situations.any();
    }

    @Property
    void next_AnyFacts_ResultIsDescribedByTable(@ForAll("situations") Situation situation, @ForAll TacticalState from,
            @ForAll TacticalOption decision, @ForAll @IntRange(min = 0, max = 1000) int inState,
            @ForAll @IntRange(min = 0, max = 1000) int sinceSeen) {
        StateMachine machine = new StateMachine(CONFIG.rules().states(), new RuleCounter());

        StateStep step = machine.next(from, decision, Assessments.timed(situation, from, inState, sinceSeen));

        Optional<StateTransition> described = CONFIG.rules().states().stream()
                .filter(transition -> step.rule().equals(Optional.of(transition.id()))).findFirst();
        assertTrue(step.rule().isEmpty() ? step.state() == from
                        : described.isPresent() && described.get().from().contains(from)
                                && described.get().to() == step.state(),
                "Переход " + from + " → " + step.state() + " (правило " + step.rule()
                        + ") не описан таблицей states: автомат не вправе менять состояние иначе");
    }
}
