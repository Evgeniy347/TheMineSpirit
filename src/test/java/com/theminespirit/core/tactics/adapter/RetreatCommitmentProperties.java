package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.TacticalAction;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.TacticalOutcome;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;

class RetreatCommitmentProperties {
    private static final TacticsAdapterSettings SETTINGS = TacticsAdapterSettings.defaults();

    @Property(tries = 200)
    void next_AnyInputs_KeepsCurrentMethodOrTakesOutcomeMethodOrIdles(@ForAll RetreatMethod currentMethod,
            @ForAll RetreatMethod outcomeMethod, @ForAll TacticalOption option,
            @ForAll @LongRange(min = 0, max = 1000) long since, @ForAll @LongRange(min = 0, max = 400) long elapsed) {
        RetreatTracker current = currentMethod == RetreatMethod.NONE ? RetreatTracker.IDLE
                : new RetreatTracker(currentMethod, since);
        TacticalOutcome outcome = AdapterFixtures.outcome(option, TacticalAction.ENGAGE, outcomeMethod);

        RetreatTracker next = RetreatCommitment.next(current, outcome, since + elapsed, SETTINGS);

        assertTrue(next.method() == RetreatMethod.NONE || next.method() == current.method()
                || next.method() == outcome.retreatMethod(),
                "Обязательство отхода берёт только прежний способ, способ из решения или простой, получили "
                        + next + " из " + current + " и решения " + option + "/" + outcomeMethod);
    }

    @Property(tries = 200)
    void next_FightWhileWalking_AlwaysIdle(@ForAll RetreatMethod method, @ForAll @LongRange(min = 0, max = 1000) long since) {
        RetreatTracker current = method == RetreatMethod.NONE ? RetreatTracker.IDLE : new RetreatTracker(method, since);
        TacticalOutcome fight = AdapterFixtures.outcome(TacticalOption.FIGHT, TacticalAction.ENGAGE, RetreatMethod.NONE);

        RetreatTracker next = RetreatCommitment.next(current, fight, since + 5, SETTINGS);

        assertTrue(!next.retreating(), "Бой прерывает бег в любой момент: дух, которого догнали, не бежит дальше");
    }
}
