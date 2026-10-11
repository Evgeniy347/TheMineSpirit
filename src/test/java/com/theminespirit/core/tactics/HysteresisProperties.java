package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class HysteresisProperties {
    private static final TacticsConfig CONFIG = TacticsConfig.loadDefault();
    private static final Set<String> SOFT_REASONS = Set.of(Decision.BY_SCORE, Decision.HELD, Decision.SOLE_CANDIDATE,
            Decision.ALL_EXCLUDED);
    private static final int INTERVAL = 10;

    @Provide
    Arbitrary<List<Situation>> series() {
        return Situations.any().map(HysteresisProperties::healthy).list().ofMinSize(2).ofMaxSize(14);
    }

    private static Situation healthy(Situation situation) {
        SelfFacts self = situation.self();
        return Situations.withSelf(situation, Situations.withStats(self, self.maxHp(), self.armor(), self.dps()));
    }

    @Property(tries = 200)
    void decisions_RandomSeries_ChangeOnlyAfterWindowOrByHardReason(@ForAll("series") List<Situation> series) {
        DecisionRun run = new DecisionRun(CONFIG, series.get(0), 1);
        TacticalOption previous = null;
        long lastChange = 0;
        for (int i = 0; i < series.size(); i++) {
            long tick = (long) i * INTERVAL;
            Decision decision = run.step(series.get(i), tick);
            if (previous != null && decision.option() != previous) {
                boolean windowOver = tick - lastChange >= CONFIG.weights().holdTicks();
                boolean hardReason = !SOFT_REASONS.contains(decision.rule()) || decision.excluded().contains(previous);
                assertTrue(windowOver || hardReason,
                        "Решение сменилось с " + previous + " на " + decision.option() + " через " + (tick - lastChange)
                                + " тиков без жёсткой причины (правило " + decision.rule() + "): гистерезис обязан "
                                + "держать решение минимум holdTicks, кроме жёстких правил и вычёркивания");
            }
            if (previous == null || decision.option() != previous) {
                lastChange = tick;
            }
            previous = decision.option();
        }
    }
}
