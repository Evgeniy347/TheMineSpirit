package com.theminespirit.core.intellect.defense;

import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.caseOf;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.orderOf;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.pair;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.plan;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.single;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.verify;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.zombies;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TeamDefenseMobScenarioTests {
    private static final List<TeamDefenseScenarios.SquadSpec> LINE =
            List.of(pair(-100, 0), pair(-50, 0), pair(0, 0), pair(50, 0), pair(100, 0));

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.theminespirit.core.intellect.defense.TeamDefenseMobCases#mobCases")
    void mob_Scenarios_MatchEnemyTypeAndWeight(TeamDefenseScenarios.Case scenario) {
        verify(scenario);
    }

    @Test
    void mob_MixedSides_EachPostUsesItsOwnStance() {
        TeamDefensePlan plan = plan(caseOf(
                "M23",
                "по четыре моба с четырёх сторон: зомби, скелеты, криперы и пауки",
                List.of(pair(0, -90), pair(90, 0), pair(0, 90), pair(-90, 0)),
                List.of(
                        zombies(4, 0, -100), TeamDefenseScenarios.skeletons(4, 100, 0),
                        TeamDefenseScenarios.creepers(4, 0, 100), TeamDefenseScenarios.spiders(4, -100, 0))));

        assertEquals(
                CombatStance.RUSH_RANGED,
                orderOf(plan, "S4").stance(),
                "Восточный пост против скелетов сближается: иначе стоит под стрелами");
        assertEquals(
                CombatStance.STRIKE_AND_RETREAT,
                orderOf(plan, "S3").stance(),
                "Южный пост против криперов бьёт наскоком: иначе встречает взрыв вплотную");
        assertEquals(
                CombatStance.ENGAGE,
                orderOf(plan, "S1").stance(),
                "Западный пост против пауков дерётся обычным боем: пауки не требуют особой стойки");
        assertEquals(
                CombatStance.ENGAGE,
                orderOf(plan, "S2").stance(),
                "Северный пост против зомби дерётся обычным боем: зомби — базовая угроза");
    }

    @Test
    void mob_ThreatMovesToOtherEdge_HelpersFollowNewThreat() {
        TeamDefensePlan first = plan(caseOf(
                "M24",
                "10 мобов слева",
                LINE,
                List.of(zombies(10, -120, 0)),
                "S1",
                "ENGAGE",
                "S2",
                "REINFORCE",
                "S3",
                "REINFORCE",
                "S4",
                "STAY",
                "S5",
                "STAY"));
        TeamDefensePlan second = plan(caseOf(
                "M25",
                "мобы ушли на правый край: помощь идёт туда",
                LINE,
                List.of(zombies(10, 120, 0)),
                "S1",
                "STAY",
                "S2",
                "STAY",
                "S3",
                "REINFORCE",
                "S4",
                "REINFORCE",
                "S5",
                "ENGAGE"));

        assertEquals(
                SquadDecision.REINFORCE,
                orderOf(first, "S2").decision(),
                "До переключения помощи идёт к левому краю: иначе отряд стоит не там, где угроза");
        assertEquals(
                SquadDecision.STAY,
                orderOf(second, "S2").decision(),
                "После переключения левая пара больше не идёт в бой: иначе отряд бегает за прошлой угрозой");
    }

    @Test
    void mob_DefenderDies_LowerCapacityCallsHelp() {
        TeamDefensePlan alive = plan(caseOf(
                "M26",
                "пара держит четырёх мобов",
                List.of(pair(-100, 0), pair(-50, 0)),
                List.of(zombies(4, -120, 0)),
                "S1",
                "ENGAGE",
                "S2",
                "STAY"));
        TeamDefensePlan lonely = plan(caseOf(
                "M27",
                "один дух из пары погиб: ёмкости не хватает, сосед идёт на помощь",
                List.of(single(-100, 0, 1.0, 10, false, 6), pair(-50, 0)),
                List.of(zombies(4, -120, 0)),
                "S1",
                "ENGAGE",
                "S2",
                "REINFORCE"));

        assertEquals(
                SquadDecision.STAY,
                orderOf(alive, "S2").decision(),
                "Пока пара жива, сосед не уходит: иначе резерв тратится без нужды");
        assertEquals(
                SquadDecision.REINFORCE,
                orderOf(lonely, "S2").decision(),
                "После гибели духа сосед закрывает нехватку: иначе оставшийся гибнет в одиночку");
        assertTrue(
                orderOf(lonely, "S1").members() == 1,
                "В отряде остался один дух: план пересчитан по новому снимку, а не по старому");
    }
}
