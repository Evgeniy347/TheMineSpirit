package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ScorerTests {
    private static final double TOLERANCE = 1e-9;

    private static Arguments row(String name, Situation situation, double fight, double retreat, double ignore) {
        return Arguments.of(name, situation, fight, retreat, ignore);
    }

    static Stream<Arguments> handSums() {
        Situation hurt = SituationBuilder.create()
                .hpFraction(0.4)
                .enemies("zombie", 2, 5)
                .build();
        return Stream.of(
                row("врагов нет", SituationBuilder.create().build(), 0, 0.2, 0.5),
                row(
                        "один зомби в 4 блоках",
                        SituationBuilder.create().enemy("zombie", 4).build(),
                        2.0 / 3 + 2.0 / 3 - 0.12 * (1 - (20.0 / 3) / 10),
                        0.2 - 1.6 / 3 + 0.6 * 2 / 3 + 0.24 * (1 - (20.0 / 3) / 10),
                        0.5 - 2.0 * 2 / 3 - 0.18 * (1 - (20.0 / 3) / 10)),
                row(
                        "раненый против пятерых",
                        hurt,
                        -2 - 0.5 - 0.5 * (1 - 4.0 / 125) + (1 - 2.0 / 12) - 0.12 * (1 - (8.0 / 15) / 10),
                        0.2
                                + 1.6
                                + 2.0 / 3
                                + 1.2 * (1 - 4.0 / 125)
                                + 0.6 * (1 - 2.0 / 12)
                                + 0.24 * (1 - (8.0 / 15) / 10),
                        0.5 - 0.8 * (1 - 4.0 / 125) - 2.0 * (1 - 2.0 / 12) - 0.18 * (1 - (8.0 / 15) / 10)),
                row(
                        "крипер в 3.5 блоках",
                        SituationBuilder.create().enemy("creeper", 3.5).build(),
                        2 + (1 - 3.5 / 12) - 1.0 * 0.5,
                        0.2 - 1.6 + 0.6 * (1 - 3.5 / 12) + 2.0 * 0.5,
                        0.5 - 2.0 * (1 - 3.5 / 12) - 0.5 * 0.5),
                row(
                        "зомби, союзники, лечилки, голод, темнота",
                        SituationBuilder.create()
                                .enemy("zombie", 4)
                                .allies(3)
                                .healItems(3)
                                .food(0)
                                .light(0)
                                .build(),
                        2.0 / 3 + 2.0 / 3 + 0.8 + 0.3 - 0.3 - 0.2 - 0.12 * (1 - (20.0 / 3) / 10),
                        0.2 - 1.6 / 3 + 0.6 * 2 / 3 - 0.5 - 0.3 + 0.3 + 0.3 + 0.24 * (1 - (20.0 / 3) / 10),
                        0.5 - 2.0 * 2 / 3 - 0.18 * (1 - (20.0 / 3) / 10)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("handSums")
    void score_KnownSituation_EqualsHandComputedSum(
            String name, Situation situation, double fight, double retreat, double ignore) {
        Scores scores = new Scorer().score(situation, Assessments.withRoutes(situation, 0), SpecWeights.spec());

        List<Double> actual = List.of(
                scores.total(TacticalOption.FIGHT),
                scores.total(TacticalOption.RETREAT),
                scores.total(TacticalOption.IGNORE));
        for (int i = 0; i < 3; i++) {
            assertEquals(
                    List.of(fight, retreat, ignore).get(i),
                    actual.get(i),
                    TOLERANCE,
                    "Ситуация «" + name + "»: счёт варианта #" + i + " (FIGHT, RETREAT, IGNORE) — сумма вес × фактор, "
                            + "посчитанная вручную; расхождение значит ошибку в формуле фактора или в суммировании");
        }
    }

    @Test
    void score_OneCall_EvaluatesEachFactorExactlyOnce() {
        Scorer scorer = new Scorer();
        Situation situation = SituationBuilder.create().enemy("zombie", 4).build();

        scorer.score(situation, Assessments.full(situation), SpecWeights.spec());

        assertEquals(
                Factor.values().length,
                scorer.factorEvaluations(),
                "Один вызов считает каждый фактор ровно один раз: пересчёт по вариантам утроил бы стоимость тика");
    }

    @Test
    void score_ContributionsOfOption_SumToTotal() {
        Situation situation = SituationBuilder.create()
                .enemy("skeleton", 9)
                .enemy("zombie", 3)
                .build();
        Scores scores = new Scorer().score(situation, Assessments.full(situation), SpecWeights.spec());

        for (TacticalOption option : TacticalOption.values()) {
            double sum = 0;
            for (Factor factor : Factor.values()) {
                sum += scores.contribution(option, factor);
            }
            assertEquals(
                    scores.total(option),
                    sum,
                    TOLERANCE,
                    "Вклады факторов объясняют счёт " + option + ": trace показывает именно эти слагаемые");
        }
    }

    @Test
    void best_EqualScores_FollowsTieBreakOrder() {
        Situation situation = SituationBuilder.create().build();
        Assessment assessment = Assessments.full(situation);
        Scorer scorer = new Scorer();
        Set<TacticalOption> all = Set.of(TacticalOption.values());
        ScoreWeights ignoreFirst = SpecWeights.equalBias("[\"IGNORE\", \"FIGHT\", \"RETREAT\"]");
        ScoreWeights retreatFirst = SpecWeights.equalBias("[\"RETREAT\", \"FIGHT\", \"IGNORE\"]");
        ScoreWeights fightFirst = SpecWeights.equalBias("[\"FIGHT\", \"IGNORE\", \"RETREAT\"]");

        List<TacticalOption> winners = List.of(
                scorer.score(situation, assessment, ignoreFirst).best(all, ignoreFirst),
                scorer.score(situation, assessment, retreatFirst).best(all, retreatFirst),
                scorer.score(situation, assessment, fightFirst).best(all, fightFirst));

        assertEquals(
                List.of(TacticalOption.IGNORE, TacticalOption.RETREAT, TacticalOption.FIGHT),
                winners,
                "При равном счёте побеждает вариант, стоящий раньше в tieBreak: без этого результат зависел бы от порядка "
                        + "перечисления в коде");
    }

    @Test
    void best_TieAmongCandidatesOnly_IgnoresExcludedOptions() {
        Situation situation = SituationBuilder.create().build();
        ScoreWeights weights = SpecWeights.equalBias("[\"IGNORE\", \"FIGHT\", \"RETREAT\"]");
        Scores scores = new Scorer().score(situation, Assessments.full(situation), weights);

        assertEquals(
                TacticalOption.FIGHT,
                scores.best(Set.of(TacticalOption.FIGHT, TacticalOption.RETREAT), weights),
                "Вычеркнутый вариант не участвует в ничьей: побеждает первый из оставшихся по tieBreak");
    }

    @Test
    void best_NoCandidates_ThrowsInsteadOfGuessing() {
        Situation situation = SituationBuilder.create().build();
        ScoreWeights weights = SpecWeights.spec();
        Scores scores = new Scorer().score(situation, Assessments.full(situation), weights);

        assertThrows(
                IllegalArgumentException.class,
                () -> scores.best(Set.of(), weights),
                "Пустой набор кандидатов — ошибка вызывающего: тихий выбор «по умолчанию» скрыл бы дыру в жёстких правилах");
    }

    @Test
    void combine_ScalingAllFactorsByTwo_DoublesTotalsWhenBiasIsZero() {
        ScoreWeights weights = SpecWeights.withBiasZero();
        Map<Factor, Double> base = new EnumMap<>(Factor.class);
        Map<Factor, Double> doubled = new EnumMap<>(Factor.class);
        for (Factor factor : Factor.values()) {
            double value = (factor.ordinal() % 5 - 2) / 3.0;
            base.put(factor, value);
            doubled.put(factor, 2 * value);
        }

        Scores once = Scorer.combine(base, weights);
        Scores twice = Scorer.combine(doubled, weights);

        for (TacticalOption option : TacticalOption.values()) {
            assertEquals(
                    2 * once.total(option),
                    twice.total(option),
                    TOLERANCE,
                    "Счёт линеен по факторам: удвоение всех факторов (кроме BIAS) удваивает счёт " + option);
        }
    }
}
