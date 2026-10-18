package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.TacticalAction;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.TacticalOutcome;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;

class RetreatTrackerProperties {
    private static final TacticalOutcome FIGHT = AdapterFixtures.outcome(TacticalOption.FIGHT, TacticalAction.ENGAGE,
            RetreatMethod.NONE);

    @Provide
    Arbitrary<RetreatMethod> movingMethods() {
        return Arbitraries.of(RetreatMethod.HOME, RetreatMethod.COVER, RetreatMethod.KITE, RetreatMethod.WATER,
                RetreatMethod.BARRICADE, RetreatMethod.PILLAR, RetreatMethod.RALLY, RetreatMethod.AWAY);
    }

    @Provide
    Arbitrary<TacticalOutcome> outcomes() {
        return Combinators.combine(Arbitraries.of(TacticalOption.values()), Arbitraries.of(TacticalAction.values()),
                Arbitraries.of(RetreatMethod.values()))
                .as((option, action, method) -> AdapterFixtures.outcome(option, action, method));
    }

    @Property(tries = 200)
    void advance_IdleTracker_StaysIdleOrStarts(@ForAll("outcomes") TacticalOutcome outcome,
            @ForAll @LongRange(min = 0, max = 100000) long nowTick, @ForAll @IntRange(min = 1, max = 6000) int hold) {
        RetreatTracker result = RetreatTracker.IDLE.advance(outcome, nowTick, hold);

        assertTrue(result.equals(RetreatTracker.IDLE) || (result.retreating() && result.sinceTick() == nowTick),
                "Из простоя ход даёт только простой или начало отхода с текущего тика, получили " + result);
    }

    @Property(tries = 200)
    void advance_Hold_DoesNotOutlastHoldTicks(@ForAll("movingMethods") RetreatMethod method,
            @ForAll @LongRange(min = 0, max = 100000) long since, @ForAll @IntRange(min = 1, max = 200) int hold) {
        RetreatTracker tracker = new RetreatTracker(method, since);

        RetreatTracker lastHeld = tracker.advance(FIGHT, since + hold - 1, hold);
        RetreatTracker released = tracker.advance(FIGHT, since + hold, hold);

        assertTrue(lastHeld.retreating(),
                "За тик до исхода удержания способ ещё исполняется: иначе дух бросит манёвр раньше срока");
        assertEquals(RetreatTracker.IDLE, released,
                "На исходе удержания отход снимается: удержание не длится больше HOLD_TICKS");
    }

    @Property(tries = 200)
    void advance_SameMethod_KeepsStartTick(@ForAll("movingMethods") RetreatMethod method,
            @ForAll @LongRange(min = 0, max = 100000) long since, @ForAll @LongRange(min = 1, max = 1000) long extra) {
        RetreatTracker tracker = new RetreatTracker(method, since);
        TacticalOutcome retreat = AdapterFixtures.outcome(TacticalOption.RETREAT, TacticalAction.RETREAT, method);

        assertEquals(tracker, tracker.advance(retreat, since + extra, 200),
                "Тот же способ отхода не сбрасывает прогресс: иначе удержание начиналось бы заново каждый тик");
    }
}
