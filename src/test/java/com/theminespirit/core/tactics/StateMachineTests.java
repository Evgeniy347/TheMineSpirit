package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StateMachineTests {
    private static final TacticalOption FIGHT = TacticalOption.FIGHT;
    private static final TacticalOption RETREAT = TacticalOption.RETREAT;
    private static final TacticalOption IGNORE = TacticalOption.IGNORE;

    private static StateMachine machine() {
        return new StateMachine(TacticsConfig.loadDefault().rules().states(), new RuleCounter());
    }

    private static Situation withEnemy(double hpFraction) {
        return SituationBuilder.create()
                .hpFraction(hpFraction)
                .enemy("zombie", 6)
                .build();
    }

    private static Situation quiet(double hpFraction) {
        return SituationBuilder.create().hpFraction(hpFraction).build();
    }

    private static Situation hiddenEnemy() {
        return SituationBuilder.create()
                .enemy(new EnemyFact("minecraft:zombie", "h", new Point(6, 0, 0), 1, 0, false, true))
                .build();
    }

    private static Arguments row(
            String name,
            TacticalState from,
            TacticalOption decision,
            Situation situation,
            long inState,
            long sinceSeen,
            TacticalState expected,
            String rule) {
        return Arguments.of(name, from, decision, situation, inState, sinceSeen, expected, rule);
    }

    static Stream<Arguments> table() {
        TacticalState calm = TacticalState.CALM;
        TacticalState engaged = TacticalState.ENGAGED;
        TacticalState retreating = TacticalState.RETREATING;
        TacticalState recovering = TacticalState.RECOVERING;
        return Stream.of(
                row("покой, решили драться", calm, FIGHT, withEnemy(1), 0, 0, engaged, "calm_to_engaged"),
                row("покой, решили отойти", calm, RETREAT, withEnemy(1), 0, 0, retreating, "to_retreating"),
                row("покой, игнор", calm, IGNORE, quiet(1), 0, 1000, calm, ""),
                row("бой, решили отойти", engaged, RETREAT, withEnemy(0.5), 5, 0, retreating, "to_retreating"),
                row("бой продолжается", engaged, FIGHT, withEnemy(1), 50, 0, engaged, ""),
                row("врагов нет 20 тиков", engaged, IGNORE, quiet(1), 50, 20, calm, "engaged_to_calm"),
                row("врагов нет 19 тиков", engaged, IGNORE, quiet(1), 50, 19, engaged, ""),
                row("враг есть, хоть и игнорируем", engaged, IGNORE, withEnemy(1), 50, 100, engaged, ""),
                row(
                        "отход, но пришлось драться",
                        retreating,
                        FIGHT,
                        withEnemy(0.5),
                        30,
                        0,
                        engaged,
                        "retreating_to_engaged"),
                row(
                        "враг не виден 60 тиков",
                        retreating,
                        RETREAT,
                        quiet(0.5),
                        100,
                        60,
                        recovering,
                        "retreating_to_recovering"),
                row("враг не виден 59 тиков", retreating, IGNORE, quiet(0.5), 100, 59, retreating, ""),
                row("враг виден, отход продолжается", retreating, RETREAT, withEnemy(0.5), 100, 100, retreating, ""),
                row(
                        "враг за стеной: не виден, но есть в данных",
                        retreating,
                        IGNORE,
                        hiddenEnemy(),
                        100,
                        60,
                        recovering,
                        "retreating_to_recovering"),
                row(
                        "восстановление, враг снова опасен",
                        recovering,
                        RETREAT,
                        withEnemy(0.5),
                        10,
                        0,
                        retreating,
                        "recovering_to_retreating"),
                row(
                        "восстановление, пришлось драться",
                        recovering,
                        FIGHT,
                        withEnemy(0.9),
                        10,
                        0,
                        engaged,
                        "recovering_to_engaged"),
                row("восстановление, HP 70 %", recovering, IGNORE, quiet(0.7), 10, 500, calm, "recovering_healed"),
                row("восстановление, HP 69 % и 599 тиков", recovering, IGNORE, quiet(0.69), 599, 500, recovering, ""),
                row(
                        "восстановление затянулось на 600 тиков",
                        recovering,
                        IGNORE,
                        quiet(0.5),
                        600,
                        500,
                        calm,
                        "recovering_timeout"),
                row(
                        "здоров, но враг опасен: отход сильнее выздоровления",
                        recovering,
                        RETREAT,
                        withEnemy(0.9),
                        10,
                        0,
                        retreating,
                        "recovering_to_retreating"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("table")
    void next_StateDecisionAndFacts_FollowsTransitionTable(
            String name,
            TacticalState from,
            TacticalOption decision,
            Situation situation,
            long inState,
            long sinceSeen,
            TacticalState expected,
            String rule) {
        FactMap facts = Assessments.timed(situation, from, inState, sinceSeen);

        StateStep step = machine().next(from, decision, facts);

        assertEquals(
                List.of(expected, rule),
                List.of(step.state(), step.rule().orElse("")),
                "Переход «" + name + "»: состояние боя меняется только по таблице states, иначе дух застрянет в бою "
                        + "или забудет об отходе");
    }

    @Test
    void transitions_Table_EveryStateReachesCalm() {
        List<StateTransition> table = TacticsConfig.loadDefault().rules().states();
        for (TacticalState start : TacticalState.values()) {
            Set<TacticalState> seen = EnumSet.of(start);
            Deque<TacticalState> queue = new ArrayDeque<>(List.of(start));
            while (!queue.isEmpty()) {
                TacticalState state = queue.poll();
                for (StateTransition transition : table) {
                    if (transition.from().contains(state) && seen.add(transition.to())) {
                        queue.add(transition.to());
                    }
                }
            }
            assertTrue(
                    seen.contains(TacticalState.CALM),
                    "Из состояния " + start + " по таблице states нельзя дойти до CALM: это тупик, дух остался бы "
                            + "в бою навсегда. Добавьте переход к покою");
        }
    }

    @Test
    void next_QuietFacts_AnyStateReachesCalmInThreeSteps() {
        Situation calmWorld = quiet(1);
        for (TacticalState start : TacticalState.values()) {
            TacticalState state = start;
            int steps = 0;
            while (state != TacticalState.CALM && steps < 3) {
                state = machine()
                        .next(state, IGNORE, Assessments.timed(calmWorld, state, 1000, 1000))
                        .state();
                steps++;
            }
            assertEquals(
                    TacticalState.CALM,
                    state,
                    "При тишине (нет врагов, HP полное, прошло много тиков) из " + start
                            + " возвращаемся в CALM не более чем за 3 шага");
        }
    }
}
