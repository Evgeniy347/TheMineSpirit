package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

class FactorProperties {
    private static final double SLACK = 1e-12;

    @Provide
    Arbitrary<Situation> situations() {
        return Situations.any();
    }

    private static EnemyMetrics metrics(Situation situation) {
        return EnemyMetrics.of(situation, Assessments.profiles(), Scales.defaults().armor());
    }

    private static double margin(Situation situation) {
        return WinMargin.of(situation.self(), metrics(situation), Scales.defaults());
    }

    @Property(tries = 200)
    void factors_AnyAdmissibleSituation_AreFiniteAndWithinRange(@ForAll("situations") Situation situation,
            @ForAll @IntRange(min = 0, max = 7) int routes, @ForAll @IntRange(min = -20, max = 40) int leash) {
        double leashLeft = leash == 40 ? Assessment.UNBOUNDED : leash;
        EnemyMetrics metrics = metrics(situation);
        Assessment assessment = new Assessment(metrics, WinMargin.of(situation.self(), metrics, Scales.defaults()),
                routes, situation.orders().mode(), true, 0, leashLeft, 100, 10,
                TacticalState.CALM, 0, 1000);

        for (Factor factor : Factor.values()) {
            double value = factor.value(situation, assessment, Scales.defaults());
            assertTrue(Double.isFinite(value) && value >= -1 && value <= 1,
                    "Фактор " + factor + " обязан быть конечным и лежать в [-1; 1], а получили " + value
                            + ": иначе один фактор перевесит все правила и веса");
        }
    }

    @Property(tries = 200)
    void winMargin_MoreArmorHpOrDamage_NeverDecreases(@ForAll("situations") Situation situation,
            @ForAll("steps") int armorStep, @ForAll("steps") int hpStep, @ForAll("steps") int dpsStep) {
        SelfFacts self = situation.self();
        double armor = Math.min(20, self.armor() + armorStep);
        double hp = Math.min(self.maxHp(), self.hp() + hpStep);
        double dps = self.dps() + dpsStep;
        Situation stronger = Situations.withSelf(situation, Situations.withStats(self, hp, armor, dps));

        assertTrue(margin(stronger) >= margin(situation) - SLACK,
                "Больше брони, HP или урона у нас — запас победы не убывает: " + margin(situation) + " → "
                        + margin(stronger) + "; иначе дух отступает именно после усиления");
    }

    @Provide
    Arbitrary<Integer> steps() {
        return Arbitraries.integers().between(0, 6);
    }

    @Property(tries = 200)
    void winMargin_MoreEnemiesOfSameType_NeverIncreases(@ForAll("situations") Situation situation) {
        if (situation.enemies().isEmpty()) {
            return;
        }
        List<EnemyFact> more = new ArrayList<>(situation.enemies());
        EnemyFact copy = situation.enemies().get(0);
        more.add(new EnemyFact(copy.type(), copy.id() + "+", copy.pos(), copy.hpFraction(), copy.armor(),
                copy.visible(), copy.targetingSelf(), copy.count()));
        Situation crowded = Situations.withEnemies(situation, more);

        assertTrue(margin(crowded) <= margin(situation) + SLACK,
                "Больше врагов того же типа — запас победы не растёт: " + margin(situation) + " → " + margin(crowded)
                        + "; иначе подкрепление врага заставляло бы духа смелеть");
    }
}
