package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

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

    @Provide
    Arbitrary<TacticalOption> options() {
        return Arbitraries.of(TacticalOption.values());
    }

    @Property(tries = 300)
    void decide_PreviousFromRule_EqualsDecisionWithoutMemory(@ForAll("healthy") Situation situation,
            @ForAll("options") TacticalOption previous, @ForAll @IntRange(min = 0, max = 100) int decidedAgo) {
        Decider decider = new Decider(new HardRules(CONFIG.rules().hard(), new RuleCounter()), CONFIG.weights());
        Assessment assessment = Assessments.withRoutes(situation, 1);
        Scores scores = new Scorer().score(situation, assessment, CONFIG.weights());
        FactMap facts = FactMap.from(situation, assessment);

        Decision afterRule = decider.decide(facts, scores, 1, Memories.decidedByRule(situation, previous, 1000 - decidedAgo),
                1000);
        Decision withoutMemory = decider.decide(facts, scores, 1, Memories.fresh(situation), 1000);

        assertEquals(List.of(withoutMemory.option(), withoutMemory.rule(), withoutMemory.excluded()),
                List.of(afterRule.option(), afterRule.rule(), afterRule.excluded()),
                "Решение, принятое жёстким правилом (" + previous + " " + decidedAgo + " тиков назад), не создаёт "
                        + "удержания: решение равно решению без памяти. Если удержание правила нужно осознанно, "
                        + "пересмотрите Hysteresis.status");
    }

    @Provide
    Arbitrary<Situation> healthy() {
        return Situations.any().map(HysteresisProperties::healthy);
    }

    @Property(tries = 200)
    void decisions_RandomSeries_ChangeOnlyAfterWindowOrByHardReason(@ForAll("series") List<Situation> series) {
        DecisionRun run = new DecisionRun(CONFIG, series.get(0), 1);
        TacticalOption previous = null;
        DecisionSource previousSource = DecisionSource.SCORE;
        long lastChange = 0;
        for (int i = 0; i < series.size(); i++) {
            long tick = (long) i * INTERVAL;
            Decision decision = run.step(series.get(i), tick);
            if (previous != null && decision.option() != previous) {
                boolean windowOver = tick - lastChange >= CONFIG.weights().holdTicks();
                boolean hardReason = !SOFT_REASONS.contains(decision.rule()) || decision.excluded().contains(previous)
                        || previousSource == DecisionSource.RULE;
                assertTrue(windowOver || hardReason,
                        "Решение сменилось с " + previous + " на " + decision.option() + " через " + (tick - lastChange)
                                + " тиков без жёсткой причины (правило " + decision.rule() + "): гистерезис обязан "
                                + "держать решение минимум holdTicks, кроме жёстких правил и вычёркивания");
            }
            if (previous == null || decision.option() != previous || decision.source() != previousSource) {
                lastChange = tick;
            }
            previous = decision.option();
            previousSource = decision.source();
        }
    }
}
