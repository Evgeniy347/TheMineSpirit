package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HardRulesTests {
    private static final Set<TacticalOption> NOTHING = Set.of();

    private static HardRules hardRules() {
        return new HardRules(TacticsConfig.loadDefault().rules().hard(), new RuleCounter());
    }

    private static SituationBuilder world() {
        return SituationBuilder.create();
    }

    private static Arguments row(String name, Situation situation, int routes, double leashLeft, double engageLeft,
            TacticalState state, String decisive, Set<TacticalOption> excluded) {
        return Arguments.of(name, situation, routes, leashLeft, engageLeft, state, decisive, excluded);
    }

    private static Arguments plain(String name, Situation situation, int routes, String decisive) {
        return row(name, situation, routes, Assessment.UNBOUNDED, Assessment.UNBOUNDED, TacticalState.CALM, decisive,
                NOTHING);
    }

    static Stream<Arguments> boundaries() {
        return Stream.of(
                plain("нет врагов: игнор", world().build(), 1, "no_enemies_ignore"),
                plain("один враг: игнор не срабатывает", world().enemy("zombie", 10).build(), 1, ""),
                plain("загнан в угол: враг в 3 блоках", world().enemy("zombie", 3).build(), 0, "cornered_fight"),
                plain("враг чуть дальше 3 блоков: не загнан", world().enemy("zombie", 3.01).build(), 0, ""),
                plain("отход есть: не загнан", world().enemy("zombie", 3).build(), 1, ""),
                plain("HP чуть ниже порога", world().hp(4.9).enemy("zombie", 10).build(), 1, "critical_hp_retreat"),
                plain("HP ровно на пороге", world().hp(5).enemy("zombie", 10).build(), 1, ""),
                plain("крипер ровно в 5 блоках", world().enemy("creeper", 5).build(), 1, "explosive_close_retreat"),
                plain("крипер чуть дальше 5", world().enemy("creeper", 5.01).build(), 1, ""),
                plain("пассивный режим: любой враг", world().mode(CombatMode.PASSIVE).enemy("zombie", 20).build(), 1,
                        "passive_never_fights"),
                plain("агрессивный режим: не пассивный", world().enemy("zombie", 20).build(), 1, ""),
                row("лава ровно в 2 блоках: нельзя драться", world().lava(2).enemy("zombie", 10).build(), 1,
                        Assessment.UNBOUNDED, Assessment.UNBOUNDED, TacticalState.CALM, "", Set.of(TacticalOption.FIGHT)),
                row("лава в 2.01 блока: можно", world().lava(2.01).enemy("zombie", 10).build(), 1,
                        Assessment.UNBOUNDED, Assessment.UNBOUNDED, TacticalState.CALM, "", NOTHING),
                row("за поводком в режиме охраны", world().mode(CombatMode.GUARD_AREA).zone(0, 0, 10)
                        .enemy("zombie", 10).build(), 1, -1, Assessment.UNBOUNDED, TacticalState.ENGAGED,
                        "outside_leash_ignore", NOTHING),
                row("на самой границе поводка", world().mode(CombatMode.GUARD_AREA).zone(0, 0, 10)
                        .enemy("zombie", 10).build(), 1, 0, Assessment.UNBOUNDED, TacticalState.ENGAGED, "", NOTHING),
                row("за поводком, но враг в 3 блоках", world().mode(CombatMode.DEFENSIVE).enemy("zombie", 3).build(),
                        1, -1, Assessment.UNBOUNDED, TacticalState.ENGAGED, "", NOTHING),
                row("за поводком в агрессивном режиме: правило не действует", world().enemy("zombie", 10).build(), 1,
                        -5, Assessment.UNBOUNDED, TacticalState.ENGAGED, "", NOTHING),
                plain("оборонительный режим не нападает первым: враг не целится в нас",
                        world().mode(CombatMode.DEFENSIVE).enemy(new EnemyFact("minecraft:zombie", "z",
                                new Point(10, 0, 0), 1, 0, true, false)).build(), 1, "uninitiated_ignore"),
                plain("оборонительный режим защищается: враг целится в нас",
                        world().mode(CombatMode.DEFENSIVE).enemy("zombie", 10).build(), 1, ""),
                row("враг вне радиуса реакции и не целится", world().enemy(new EnemyFact("minecraft:zombie", "z",
                        new Point(30, 0, 0), 1, 0, true, false)).build(), 1, Assessment.UNBOUNDED, -6,
                        TacticalState.CALM, "outside_engage_ignore", NOTHING),
                row("уже в бою: радиус реакции не применяется", world().enemy(new EnemyFact("minecraft:zombie", "z",
                        new Point(30, 0, 0), 1, 0, true, false)).build(), 1, Assessment.UNBOUNDED, -6,
                        TacticalState.ENGAGED, "", NOTHING),
                row("приоритет: загнан в угол сильнее критического HP", world().hp(4).enemy("zombie", 2).build(), 0,
                        Assessment.UNBOUNDED, Assessment.UNBOUNDED, TacticalState.CALM, "cornered_fight", NOTHING),
                plain("приоритет: критическое HP сильнее крипера и лавы", world().hp(4).lava(1).enemy("creeper", 4)
                        .build(), 1, "critical_hp_retreat"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("boundaries")
    void evaluate_RuleBoundary_FiresOnlyWhenConditionHolds(String name, Situation situation, int routes,
            double leashLeft, double engageLeft, TacticalState state, String decisive, Set<TacticalOption> excluded) {
        FactMap facts = FactMap.from(situation, Assessments.custom(situation, routes, leashLeft, engageLeft, state));

        HardVerdict verdict = hardRules().evaluate(facts);

        assertEquals(List.of(decisive, excluded),
                List.of(verdict.decisive().map(HardRule::id).orElse(""), verdict.excluded()),
                "Жёсткое правило «" + name + "»: срабатывает ровно на своей границе, иначе дух бросит бой раньше или "
                        + "позже, чем задумано в rules.json");
    }

    @Test
    void evaluate_TwoDecisiveRules_HigherPriorityWins() {
        Situation cornered = world().hp(4).enemy("zombie", 2).build();
        TacticsConfig swapped = withRules(TacticsTexts.mutate(TacticsTexts.mutate(
                TacticsTexts.read("rules.json"), "\"priority\": 95", "\"priority\": 89"), "\"priority\": 90",
                "\"priority\": 96"));
        FactMap facts = FactMap.from(cornered, Assessments.custom(cornered, 0, Assessment.UNBOUNDED,
                Assessment.UNBOUNDED, TacticalState.CALM));

        String normal = hardRules().evaluate(facts).decisive().get().id();
        String reversed = new HardRules(swapped.rules().hard(), new RuleCounter()).evaluate(facts).decisive().get().id();

        assertEquals(List.of("cornered_fight", "critical_hp_retreat"), List.of(normal, reversed),
                "Из двух подходящих правил с решением выигрывает старшее по приоритету: порядок задаёт rules.json, "
                        + "а не порядок перечисления в коде");
    }

    private static TacticsConfig withRules(String rules) {
        return TacticsConfig.fromJson(TacticsTexts.read("enemies.json"), TacticsTexts.read("weights.json"), rules,
                TacticsTexts.read("modes.json"));
    }

    @Test
    void evaluate_DecisionRuleAboveExcludeRule_StopsBeforeLowerRules() {
        Situation burning = world().lava(1).enemy("creeper", 4).build();
        FactMap facts = Assessments.facts(burning);
        RuleCounter counter = new RuleCounter();

        HardVerdict verdict = new HardRules(TacticsConfig.loadDefault().rules().hard(), counter).evaluate(facts);

        assertEquals(List.of("explosive_close_retreat", 4L),
                List.of(verdict.decisive().get().id(), counter.checks()),
                "После первого подходящего правила с решением остальные не проверяются: стоимость вызова ограничена "
                        + "числом правил до него (no_enemies, cornered, critical, explosive)");
    }
}
