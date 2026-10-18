package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;

class LowHpRetreatTests {
    private static final TacticsConfig CONFIG = TacticsConfig.loadDefault();
    private static final double HOME = 20;

    private static TacticalOutcome decide(Situation situation) {
        return new TacticsEngine(CONFIG).step(situation, Memories.fresh(situation), 0);
    }

    @Provide
    Arbitrary<Double> lowHp() {
        return Arbitraries.doubles().between(0.3, 0.4);
    }

    @Property(tries = 200)
    void step_LowHpSingleMeleeEnemyAndHomeRoute_RetreatsInsteadOfFighting(@ForAll("lowHp") double hpFraction,
            @ForAll @DoubleRange(min = 1, max = 20) double dps,
            @ForAll @DoubleRange(min = 3.5, max = 10) double distance) {
        Situation situation = SituationBuilder.create().hpFraction(hpFraction).dps(dps).home(HOME)
                .enemy("zombie", distance).build();

        TacticalOutcome outcome = decide(situation);

        assertEquals(TacticalOption.RETREAT, outcome.decision(),
                "HP " + Math.round(hpFraction * 100) + " %, урон " + dps + ", зомби в " + distance
                        + " блоках: при низком HP отход обязан перевесить атаку даже у сильного духа, иначе "
                        + "вес RETREAT в weights.json снова стал малым. Трасса: " + outcome.trace().explain());
    }

    @Example
    void step_HpFortyPercentStrongSwordZombieAdjacent_RetreatsByLargerLowHpWeight() {
        Situation situation = SituationBuilder.create().hpFraction(0.4).dps(20).home(HOME)
                .enemy("zombie", 3.5).build();

        TacticalOutcome outcome = decide(situation);

        assertEquals(TacticalOption.RETREAT, outcome.decision(),
                "HP 40 % и сильный меч против одного зомби в 3.5 блоках при доме в 20: вес LOW_HP поднят "
                        + "(RETREAT 2.5, FIGHT −2.0), поэтому низкое HP побеждает атаку; иначе отход снова решает "
                        + "перевес боя. Трасса: " + outcome.trace().explain());
    }
}
