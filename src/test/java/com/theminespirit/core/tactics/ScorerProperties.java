package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class ScorerProperties {
    private static final Set<TacticalOption> ALL = Set.of(TacticalOption.values());
    private static final ScoreWeights WEIGHTS = TacticsConfig.loadDefault().weights();

    @Provide
    Arbitrary<Situation> situations() {
        return Situations.any();
    }

    @Provide
    Arbitrary<Integer> steps() {
        return Arbitraries.integers().between(1, 20);
    }

    private static TacticalOption choose(Situation situation) {
        Scores scores = new Scorer().score(situation, Assessments.full(situation), WEIGHTS);
        return scores.best(ALL, WEIGHTS);
    }

    @Property
    void choose_MoreHp_NeverTurnsFightIntoRetreat(@ForAll("situations") Situation situation,
            @ForAll("steps") int step) {
        SelfFacts self = situation.self();
        double healthier = Math.min(self.maxHp(), self.hp() + step);
        Situation recovered = Situations.withSelf(situation,
                Situations.withStats(self, healthier, self.armor(), self.dps()));

        if (choose(situation) == TacticalOption.FIGHT) {
            assertNotEquals(TacticalOption.RETREAT, choose(recovered),
                    "Рост HP не превращает FIGHT в RETREAT при прочих равных: иначе дух отступает именно после лечения");
        }
    }

    @Property
    void fightOverRetreat_MoreHp_NeverDecreases(@ForAll("situations") Situation situation, @ForAll("steps") int step) {
        SelfFacts self = situation.self();
        Situation recovered = Situations.withSelf(situation,
                Situations.withStats(self, Math.min(self.maxHp(), self.hp() + step), self.armor(), self.dps()));

        assertTrue(preferenceForFight(recovered) >= preferenceForFight(situation) - 1e-9,
                "Рост HP не уменьшает перевес FIGHT над RETREAT: " + preferenceForFight(situation) + " → "
                        + preferenceForFight(recovered) + "; знак веса LOW_HP или WIN_MARGIN у RETREAT перепутан");
    }

    private static double preferenceForFight(Situation situation) {
        Scores scores = new Scorer().score(situation, Assessments.full(situation), WEIGHTS);
        return scores.total(TacticalOption.FIGHT) - scores.total(TacticalOption.RETREAT);
    }

    @Property
    void choose_MoreEnemiesOfSameKind_NeverTurnsRetreatIntoFight(@ForAll("situations") Situation situation) {
        if (situation.enemies().isEmpty()) {
            return;
        }
        List<EnemyFact> more = new ArrayList<>(situation.enemies());
        EnemyFact copy = situation.enemies().get(0);
        more.add(new EnemyFact(copy.type(), copy.id() + "+", copy.pos(), copy.hpFraction(), copy.armor(),
                copy.visible(), copy.targetingSelf(), copy.count()));
        Situation crowded = Situations.withEnemies(situation, more);

        if (choose(situation) == TacticalOption.RETREAT) {
            assertNotEquals(TacticalOption.FIGHT, choose(crowded),
                    "Рост числа врагов не превращает RETREAT в FIGHT при прочих равных: иначе подкрепление врага "
                            + "делало бы духа смелее");
        }
    }
}
