package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ActionTableTests {
    private static SituationBuilder world() {
        return SituationBuilder.create();
    }

    private static Arguments row(
            String name, Situation situation, TacticalOption decision, TacticalAction action, String rule) {
        return Arguments.of(name, situation, decision, action, rule);
    }

    private static EnemyFact hidden(double distance) {
        return new EnemyFact("minecraft:zombie", "h", new Point(distance, 0, 0), 1, 0, false, true);
    }

    static Stream<Arguments> table() {
        TacticalOption fight = TacticalOption.FIGHT;
        return Stream.of(
                row(
                        "лук есть, враг в 10",
                        world().ammo(10).enemy("zombie", 10).build(),
                        fight,
                        TacticalAction.SHOOT,
                        "shoot_at_range"),
                row(
                        "лук есть, враг в 5: граница включительно",
                        world().ammo(10).enemy("zombie", 5).build(),
                        fight,
                        TacticalAction.SHOOT,
                        "shoot_at_range"),
                row(
                        "лук есть, враг в 4.9: слишком близко",
                        world().ammo(10).enemy("zombie", 4.9).build(),
                        fight,
                        TacticalAction.ENGAGE,
                        "engage_default"),
                row(
                        "лук есть, враг не виден",
                        world().ammo(10).enemy(hidden(10)).build(),
                        fight,
                        TacticalAction.ENGAGE,
                        "engage_default"),
                row(
                        "стрел нет, враг в 10",
                        world().enemy("zombie", 10).build(),
                        fight,
                        TacticalAction.ENGAGE,
                        "engage_default"),
                row(
                        "охрана зоны без лука",
                        world().mode(CombatMode.GUARD_AREA)
                                .zone(0, 0, 10)
                                .enemy("zombie", 6)
                                .build(),
                        fight,
                        TacticalAction.DEFEND_AREA,
                        "defend_area"),
                row(
                        "охрана зоны с луком: стрельба выше по приоритету",
                        world().mode(CombatMode.GUARD_AREA)
                                .zone(0, 0, 10)
                                .ammo(5)
                                .enemy("zombie", 8)
                                .build(),
                        fight,
                        TacticalAction.SHOOT,
                        "shoot_at_range"),
                row(
                        "защита подопечного, его бьют",
                        world().mode(CombatMode.PROTECT)
                                .ward(1, 1, true)
                                .enemy("zombie", 6)
                                .build(),
                        fight,
                        TacticalAction.PROTECT,
                        "protect_ward"),
                row(
                        "защита подопечного, угрозы нет",
                        world().mode(CombatMode.PROTECT)
                                .ward(1, 1, false)
                                .enemy("zombie", 6)
                                .build(),
                        fight,
                        TacticalAction.ENGAGE,
                        "engage_default"),
                row(
                        "защита и лук: стрельба выше",
                        world().mode(CombatMode.PROTECT)
                                .ward(1, 1, true)
                                .ammo(5)
                                .enemy("zombie", 9)
                                .build(),
                        fight,
                        TacticalAction.SHOOT,
                        "shoot_at_range"),
                row(
                        "агрессивный бой",
                        world().enemy("zombie", 3).build(),
                        fight,
                        TacticalAction.ENGAGE,
                        "engage_default"),
                row(
                        "оборонительный бой",
                        world().mode(CombatMode.DEFENSIVE).enemy("zombie", 3).build(),
                        fight,
                        TacticalAction.ENGAGE,
                        "engage_default"),
                row(
                        "отход",
                        world().enemy("zombie", 3).build(),
                        TacticalOption.RETREAT,
                        TacticalAction.RETREAT,
                        "retreat_default"),
                row(
                        "отход с луком не превращается в стрельбу",
                        world().ammo(5).enemy("zombie", 10).build(),
                        TacticalOption.RETREAT,
                        TacticalAction.RETREAT,
                        "retreat_default"),
                row(
                        "игнор",
                        world().enemy("zombie", 30).build(),
                        TacticalOption.IGNORE,
                        TacticalAction.CONTINUE,
                        "continue_default"),
                row(
                        "игнор при угрозе подопечному",
                        world().mode(CombatMode.PROTECT)
                                .ward(1, 1, true)
                                .enemy("zombie", 30)
                                .build(),
                        TacticalOption.IGNORE,
                        TacticalAction.CONTINUE,
                        "continue_default"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("table")
    void choose_DecisionAndFacts_PicksActionByPriority(
            String name, Situation situation, TacticalOption decision, TacticalAction action, String rule) {
        ActionTable table = new ActionTable(TacticsConfig.loadDefault().rules().actions(), new RuleCounter());

        ActionChoice choice = table.choose(decision, Assessments.facts(situation));

        assertEquals(
                List.of(action, rule),
                List.of(choice.action(), choice.rule()),
                "Ситуация «" + name
                        + "»: действие выбирается первым подходящим правилом таблицы actions по приоритету, "
                        + "и только среди правил своего решения");
    }
}
