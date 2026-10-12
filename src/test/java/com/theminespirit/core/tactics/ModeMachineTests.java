package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ModeMachineTests {
    private static ModeMachine machine() {
        return new ModeMachine(TacticsConfig.loadDefault().rules().modeTransitions(), new RuleCounter());
    }

    private static Arguments row(String name, CombatMode ordered, CombatMode current, double hp, boolean zone,
            boolean ward, CombatMode expected, String rule) {
        return Arguments.of(name, ordered, current, hp, zone, ward, expected, rule);
    }

    static Stream<Arguments> table() {
        CombatMode aggressive = CombatMode.AGGRESSIVE;
        CombatMode defensive = CombatMode.DEFENSIVE;
        return Stream.of(
                row("агрессивный устал: HP ниже 30 %", aggressive, aggressive, 0.29, true, true, defensive,
                        "aggressive_tires"),
                row("агрессивный: HP ровно 30 % - ещё держится", aggressive, aggressive, 0.3, true, true, aggressive,
                        ""),
                row("агрессивный здоров", aggressive, aggressive, 0.9, true, true, aggressive, ""),
                row("вернулся в агрессивный при 70 %", aggressive, defensive, 0.7, true, true, aggressive,
                        "aggressive_recovers"),
                row("при 69 % остаётся оборонительным", aggressive, defensive, 0.69, true, true, defensive, ""),
                row("приказ был оборонительный: назад не тянет", defensive, defensive, 1.0, true, true, defensive, ""),
                row("защита с подопечным", CombatMode.PROTECT, CombatMode.PROTECT, 1.0, true, true,
                        CombatMode.PROTECT, ""),
                row("защита без подопечного", CombatMode.PROTECT, CombatMode.PROTECT, 1.0, true, false, defensive,
                        "protect_without_ward"),
                row("подопечный вернулся", CombatMode.PROTECT, defensive, 1.0, true, true, CombatMode.PROTECT,
                        "protect_restored"),
                row("подопечного всё нет", CombatMode.PROTECT, defensive, 1.0, true, false, defensive, ""),
                row("охрана с зоной", CombatMode.GUARD_AREA, CombatMode.GUARD_AREA, 1.0, true, true,
                        CombatMode.GUARD_AREA, ""),
                row("охрана без зоны", CombatMode.GUARD_AREA, CombatMode.GUARD_AREA, 1.0, false, true, defensive,
                        "guard_without_zone"),
                row("зона появилась", CombatMode.GUARD_AREA, defensive, 1.0, true, true, CombatMode.GUARD_AREA,
                        "guard_restored"),
                row("пассивный не меняется", CombatMode.PASSIVE, CombatMode.PASSIVE, 0.1, false, false,
                        CombatMode.PASSIVE, ""));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("table")
    void next_ModeAndFacts_FollowsTransitionTable(String name, CombatMode ordered, CombatMode current, double hp,
            boolean zone, boolean ward, CombatMode expected, String rule) {
        SituationBuilder world = SituationBuilder.create().mode(ordered).hpFraction(hp);
        if (zone) {
            world.zone(0, 0, 10);
        }
        if (ward) {
            world.ward(2, 0, false);
        }
        FactMap facts = Assessments.factsInMode(world.build(), current);

        ModeStep step = machine().next(current, facts);

        assertEquals(List.of(expected, rule), List.of(step.mode(), step.rule().orElse("")),
                "Смена режима «" + name + "»: таблица переходов modeTransitions решает, когда режим деградирует и "
                        + "когда возвращается к приказанному");
    }

    @Test
    void next_HpDipsAndRecovers_OrderedModeIsRestored() {
        ModeMachine machine = machine();
        CombatMode current = CombatMode.AGGRESSIVE;
        List<CombatMode> history = new ArrayList<>();
        for (double hp : new double[] {0.2, 0.5, 0.8}) {
            Situation situation = SituationBuilder.create().mode(CombatMode.AGGRESSIVE).hpFraction(hp).build();
            current = machine.next(current, Assessments.factsInMode(situation, current)).mode();
            history.add(current);
        }

        assertEquals(List.of(CombatMode.DEFENSIVE, CombatMode.DEFENSIVE, CombatMode.AGGRESSIVE), history,
                "Приказ стратега не теряется при временной смене: при 20 % уходим в defensive, при 50 % остаёмся, "
                        + "при 80 % возвращаемся в aggressive");
    }

    @Test
    void next_NoTransitionApplies_ReportsNoRule() {
        Situation situation = SituationBuilder.create().build();

        ModeStep step = machine().next(CombatMode.AGGRESSIVE,
                Assessments.factsInMode(situation, CombatMode.AGGRESSIVE));

        assertEquals(Optional.empty(), step.rule(), "Без перехода правило не названо: trace не выдумывает причину");
    }
}
