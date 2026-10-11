package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FactorTests {
    private static final double TOLERANCE = 1e-9;

    private static Situation lonelyZombie() {
        return SituationBuilder.create().enemy("zombie", 4).build();
    }

    private static Situation hurtAgainstFive() {
        return SituationBuilder.create().hpFraction(0.4).enemies("zombie", 2, 5).build();
    }

    private static Situation hazardousNight() {
        return SituationBuilder.create().enemy("skeleton", 12).enemy("creeper", 3.5).time(TimeOfDay.NIGHT).light(4)
                .lava(3).drop(6).food(0.25).allies(2).healItems(1).build();
    }

    private static Assessment assessment(Situation situation, int routes, double leashLeft) {
        EnemyMetrics metrics = EnemyMetrics.of(situation, Assessments.profiles(), Scales.defaults().armor());
        double margin = WinMargin.of(situation.self(), metrics, Scales.defaults());
        return new Assessment(metrics, margin, routes, situation.orders().mode(), true, 0, leashLeft, 100, 10,
                TacticalState.CALM, 0, 1000);
    }

    private static Arguments row(String name, Situation situation, int routes, double leashLeft,
            Map<Factor, Double> expected) {
        return Arguments.of(name, situation, routes, leashLeft, expected);
    }

    private static Map<Factor, Double> values(Object... pairs) {
        Map<Factor, Double> map = new EnumMap<>(Factor.class);
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((Factor) pairs[i], ((Number) pairs[i + 1]).doubleValue());
        }
        return map;
    }

    static Stream<Arguments> situations() {
        return Stream.of(
                row("один зомби, полное HP", lonelyZombie(), 0, Assessment.UNBOUNDED,
                        values(Factor.BIAS, 1, Factor.WIN_MARGIN, 1.0 / 3, Factor.LOW_HP, 0,
                                Factor.ENEMY_PRESSURE, 1.0 / 3, Factor.PROXIMITY, 2.0 / 3, Factor.RANGED_EXPOSURE, 0,
                                Factor.EXPLOSIVE_NEAR, 0, Factor.ALLY_SUPPORT, 0, Factor.ESCAPE_ROUTE, 0,
                                Factor.TERRAIN_RISK, 0, Factor.DARKNESS, 0, Factor.HUNGER, 0,
                                Factor.PROTECT_URGENCY, 0, Factor.LEASH_PRESSURE, 0, Factor.HEALING_AVAILABLE, 0)),
                row("раненый против пятерых", hurtAgainstFive(), 2, Assessment.UNBOUNDED,
                        values(Factor.WIN_MARGIN, -1, Factor.LOW_HP, 1.0 / 3, Factor.ENEMY_PRESSURE, 1 - 8.0 / 15 / 10,
                                Factor.PROXIMITY, 1 - 2.0 / 12, Factor.ESCAPE_ROUTE, 2.0 / 3)),
                row("скелет, крипер, ночь, лава и обрыв", hazardousNight(), 5, Assessment.UNBOUNDED,
                        values(Factor.RANGED_EXPOSURE, 0.5 * (1 - 12.0 / 24), Factor.EXPLOSIVE_NEAR, 0.5,
                                Factor.ALLY_SUPPORT, 2.0 / 3, Factor.ESCAPE_ROUTE, 1, Factor.TERRAIN_RISK, 0.5,
                                Factor.DARKNESS, 1 - 4.0 / 15, Factor.HUNGER, 0.5, Factor.HEALING_AVAILABLE, 1.0 / 3,
                                Factor.PROXIMITY, 1 - 3.5 / 12)),
                row("приказ защищать, подопечного бьют, поводок на исходе",
                        SituationBuilder.create().mode(CombatMode.PROTECT).ward(2, 2, true).enemy("zombie", 4).build(),
                        0, 1.0, values(Factor.PROTECT_URGENCY, 1, Factor.LEASH_PRESSURE, 0.75)),
                row("поводок превышен", SituationBuilder.create().enemy("zombie", 4).build(), 0, -3.0,
                        values(Factor.LEASH_PRESSURE, 1)),
                row("далеко от ночи в тёмной пещере без врагов", SituationBuilder.create().light(0).build(), 0,
                        Assessment.UNBOUNDED, values(Factor.DARKNESS, 1, Factor.WIN_MARGIN, 0, Factor.PROXIMITY, 0,
                                Factor.ENEMY_PRESSURE, 0, Factor.LEASH_PRESSURE, 0)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("situations")
    void value_KnownSituation_MatchesHandComputedFactor(String name, Situation situation, int routes,
            double leashLeft, Map<Factor, Double> expected) {
        Assessment assessment = assessment(situation, routes, leashLeft);

        expected.forEach((factor, wanted) -> assertEquals(wanted,
                factor.value(situation, assessment, Scales.defaults()), TOLERANCE,
                "Ситуация «" + name + "», фактор " + factor + ": значение считается по формуле из docs/TACTICS.md; "
                        + "если формулу меняют осознанно, пересчитайте таблицу и веса сценариев"));
    }

    @Test
    void factors_DeclarationOrder_IsFixed() {
        assertEquals(List.of("BIAS", "WIN_MARGIN", "LOW_HP", "ENEMY_PRESSURE", "PROXIMITY", "RANGED_EXPOSURE",
                "EXPLOSIVE_NEAR", "ALLY_SUPPORT", "ESCAPE_ROUTE", "TERRAIN_RISK", "DARKNESS", "HUNGER",
                "PROTECT_URGENCY", "LEASH_PRESSURE", "HEALING_AVAILABLE"),
                Arrays.stream(Factor.values()).map(Enum::name).toList(),
                "Порядок объявления факторов — порядок членов суммы: перестановка меняет округление счёта в последнем знаке");
    }

    @Test
    void winMargin_NoEnemies_IsZero() {
        Situation situation = SituationBuilder.create().build();
        EnemyMetrics metrics = EnemyMetrics.of(situation, Assessments.profiles(), Scales.defaults().armor());

        assertEquals(0.0, WinMargin.of(situation.self(), metrics, Scales.defaults()), 0.0,
                "Без врагов запас победы равен 0: «побеждать» нечего, решение принимают правила, а не знак отрицательного числа");
    }

    @Test
    void winMargin_ZeroHp_IsMinusOne() {
        Situation situation = SituationBuilder.create().hp(0).enemy("zombie", 4).build();
        EnemyMetrics metrics = EnemyMetrics.of(situation, Assessments.profiles(), Scales.defaults().armor());

        assertEquals(-1.0, WinMargin.of(situation.self(), metrics, Scales.defaults()), 0.0,
                "Дух с нулевым HP проигрывает гарантированно: запас -1 без деления на ноль и NaN");
    }

    @Test
    void winMargin_ArmorReducesDamage_ArmoredSelfFightsBetter() {
        Situation bare = SituationBuilder.create().enemies("zombie", 4, 3).build();
        Situation armored = SituationBuilder.create().armor(10).enemies("zombie", 4, 3).build();

        double bareMargin = margin(bare);
        double armoredMargin = margin(armored);

        assertTrue(armoredMargin > bareMargin,
                "Броня 10 (-40 % урона) повышает запас победы: " + bareMargin + " → " + armoredMargin
                        + "; иначе дух не ценит доспехи при выборе боя");
    }

    @Test
    void effectiveHp_ArmorBeyondCap_StopsGrowing() {
        ArmorModel steep = new ArmorModel(0.1, 0.8);

        assertEquals(steep.effectiveHp(10, 8), steep.effectiveHp(10, 20), 0.0,
                "Снижение урона упирается в предел 0.8: без предела полный комплект брони делал бы духа бессмертным, "
                        + "а делитель 1 - снижение уходил бы в ноль");
    }

    private static double margin(Situation situation) {
        EnemyMetrics metrics = EnemyMetrics.of(situation, Assessments.profiles(), Scales.defaults().armor());
        return WinMargin.of(situation.self(), metrics, Scales.defaults());
    }

    @Test
    void factors_SixteenEnemies_StayWithinRange() {
        Situation crowd = SituationBuilder.create().enemies("zombie", 3, Situation.MAX_ENEMIES).build();
        Assessment assessment = assessment(crowd, 3, 2);

        for (Factor factor : Factor.values()) {
            double value = factor.value(crowd, assessment, Scales.defaults());
            assertTrue(value >= -1 && value <= 1,
                    "Фактор " + factor + " вышел из [-1; 1] на MAX_ENEMIES врагах: " + value);
        }
    }
}
