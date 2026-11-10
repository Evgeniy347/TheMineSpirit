package com.theminespirit.core.intellect.defense;

import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.STAGE_ONE;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.STAGE_THREE;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.STAGE_TWO;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.caseOf;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.orderOf;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.pair;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.plan;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.ring;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.squad;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.verify;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.zombies;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TeamDefenseCircleScenarioTests {
    private static final List<TeamDefenseScenarios.SquadSpec> RING_ONE = ring(STAGE_ONE, 8);
    private static final List<TeamDefenseScenarios.SquadSpec> RING_TWO = ring(STAGE_TWO, 8);

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.theminespirit.core.intellect.defense.TeamDefenseCircleCases#circleCases")
    void circle_Scenarios_MatchSectorPlan(TeamDefenseScenarios.Case scenario) {
        verify(scenario);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.theminespirit.core.intellect.defense.TeamDefenseCircleHelpCases#circleHelpCases")
    void circleHelp_Scenarios_MatchSectorPlan(TeamDefenseScenarios.Case scenario) {
        verify(scenario);
    }

    @Test
    void circle_OneSideAtStageTwo_HelperComesFromNeighbour() {
        TeamDefensePlan plan = plan(caseOf(
                "C20", "восемь мобов с севера на втором этапе", RING_TWO, List.of(zombies(8, 0, -80)), "S4", "ENGAGE"));

        assertEquals(
                1,
                plan.orders().stream()
                        .filter(order -> order.decision() == SquadDecision.REINFORCE)
                        .count(),
                "Одной пары помощи хватает на восемь мобов: больше помощников оголит остальной периметр");
    }

    @Test
    void circle_SixteenNorth_ThreePairsHelpButPerimeterKeepsReserve() {
        TeamDefensePlan plan = plan(caseOf(
                "C21",
                "шестнадцать мобов с севера на первом этапе",
                RING_ONE,
                List.of(zombies(16, 0, -55)),
                "S4",
                "ENGAGE"));

        long helpers = plan.orders().stream()
                .filter(order -> order.decision() == SquadDecision.REINFORCE)
                .mapToInt(SquadOrder::members)
                .sum();
        assertEquals(
                6, helpers, "Предел помощи — три пары: остальные посты остаются на периметре, иначе поселение оголено");
        assertTrue(
                plan.orders().stream().anyMatch(order -> order.decision() == SquadDecision.STAY),
                "Часть постов остаётся в резерве: помощь не снимает весь периметр");
    }

    @Test
    void circle_GapInEast_TwoNearestPostsFillIt() {
        TeamDefensePlan plan = plan(caseOf(
                "C22",
                "пролом на востоке, восемь мобов с северо-востока",
                List.of(pair(0, -90), pair(-90, 0), pair(0, 90)),
                List.of(zombies(8, 70, -70)),
                "S2",
                "REINFORCE"));

        assertEquals(
                2,
                plan.orders().stream()
                        .filter(order -> order.decision() == SquadDecision.REINFORCE)
                        .count(),
                "На восемь мобов идут две ближайшие пары: одна не закрывает пролом");
    }

    @Test
    void circle_SpreadPlan_KeepsEverySectorCoveredOnce() {
        TeamDefensePlan spread = plan(caseOf(
                "C23",
                "сбежались в одну точку, мобы с двух сторон",
                List.of(squad("g", 0, 0, 4, 1.0, 10, false, 6.0, 1.0)),
                List.of(zombies(5, 0, -100), zombies(5, 0, 100)),
                "S1",
                "SPREAD"));

        assertEquals(
                2,
                orderOf(spread, "S1").sectors().size(),
                "Рассредоточение накрывает ровно угрожаемые секторы: лишние секторы растянули бы отряд");
        assertEquals(
                4,
                orderOf(spread, "S1").targets(),
                "Каждый дух расходящегося отряда получает цель: иначе половина стоит в стороне");
    }

    @Test
    void circle_NoThreats_AllPostsHoldEvenWithManySquads() {
        List<TeamDefenseScenarios.SquadSpec> big = ring(STAGE_THREE, 16);
        TeamDefensePlan plan = plan(caseOf("C24", "шестнадцать постов без угроз", big, List.of()));

        assertEquals(
                16,
                plan.orders().stream()
                        .filter(order -> order.decision() == SquadDecision.STAY)
                        .count(),
                "Без угроз все посты стоят: иначе мирное поселение теряет периметр");
    }

    @Test
    void circle_ThreeSidesLocalFight_NoUnnecessaryHelp() {
        List<TeamDefenseScenarios.SquadSpec> squads =
                new ArrayList<>(List.of(pair(0, -90), pair(-90, 0), pair(0, 90), pair(90, 0)));
        TeamDefensePlan plan = plan(caseOf(
                "C25",
                "по три моба с трёх сторон: дерутся три поста, четвёртый не бежит",
                squads,
                List.of(zombies(3, 0, -100), zombies(3, -100, 0), zombies(3, 0, 100)),
                "S1",
                "ENGAGE",
                "S2",
                "ENGAGE",
                "S3",
                "ENGAGE",
                "S4",
                "STAY"));

        assertEquals(
                4,
                plan.orders().size(),
                "Каждый пост получает приказ, даже если не дерётся: иначе часть духов без управления");
    }

    @Test
    void circle_BigGarrison_FourSidesEachKeepsLocalFighter() {
        TeamDefensePlan plan = plan(caseOf(
                "C26",
                "большой гарнизон: шестнадцать постов, четыре стороны",
                ring(STAGE_THREE, 16),
                List.of(zombies(4, 0, -100), zombies(4, 100, 0), zombies(4, 0, 100), zombies(4, -100, 0))));

        assertEquals(
                4,
                plan.orders().stream()
                        .filter(order -> order.decision() == SquadDecision.ENGAGE)
                        .count(),
                "Каждую сторону встречает свой пост: иначе на одной стороне дерутся, а другую некому закрыть");
        assertEquals(
                16, plan.orders().size(), "Все посты получают приказ: иначе часть гарнизона остаётся без управления");
    }
}
