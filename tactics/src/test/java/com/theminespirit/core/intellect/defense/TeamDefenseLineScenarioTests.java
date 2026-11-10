package com.theminespirit.core.intellect.defense;

import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.WEIGHTS;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.caseOf;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.orderOf;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.pair;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.plan;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.verify;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.zombies;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.CoordinatorState;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TeamDefenseLineScenarioTests {
    private static final List<TeamDefenseScenarios.SquadSpec> LINE =
            List.of(pair(-100, 0), pair(-50, 0), pair(0, 0), pair(50, 0), pair(100, 0));

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.theminespirit.core.intellect.defense.TeamDefenseLineCases#lineCases")
    void line_Scenarios_MatchOwnerCases(TeamDefenseScenarios.Case scenario) {
        verify(scenario);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.theminespirit.core.intellect.defense.TeamDefenseLineHelpCases#helpCases")
    void lineHelp_Scenarios_MatchOwnerCases(TeamDefenseScenarios.Case scenario) {
        verify(scenario);
    }

    @Test
    void line_StrongestEdge_GetsTwoPairsNearestToIt() {
        TeamDefensePlan plan = plan(caseOf(
                "L21",
                "10 мобов на левый край: пара S2 ближе S3 и идёт первой",
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

        assertEquals(
                Optional.of("S1"),
                orderOf(plan, "S2").reinforce(),
                "Помощь адресуется конкретной паре на краю: иначе духи бегут в пустой сектор");
        assertEquals(
                Optional.of("S1"),
                orderOf(plan, "S3").reinforce(),
                "Вторая помощница идёт к тому же краю: две пары закрывают сильнейшую угрозу");
    }

    @Test
    void line_NoThreats_NoOneLeavesPost() {
        TeamDefensePlan plan = plan(caseOf(
                "L22",
                "мирная шеренга: никто не сходит с места",
                LINE,
                List.of(),
                "S1",
                "STAY",
                "S2",
                "STAY",
                "S3",
                "STAY",
                "S4",
                "STAY",
                "S5",
                "STAY"));

        assertEquals(
                0,
                plan.memberOrders().stream()
                        .filter(order -> order.target().isPresent())
                        .count(),
                "Без угроз ни у кого нет цели: иначе мирная шеренга срывается с постов");
        assertEquals(
                5,
                plan.memberOrders().stream().map(MemberOrder::post).distinct().count(),
                "Каждая пара остаётся на своей точке: иначе резерв сбегается в кучу");
    }

    @Test
    void planner_SpreadDefenders_UsesTeamPosts() {
        List<DefenderCapability> defenders = new ArrayList<>();
        for (int pair = 0; pair < 5; pair++) {
            for (int member = 0; member < 2; member++) {
                defenders.add(new DefenderCapability(
                        UUID.nameUUIDFromBytes((pair + "-" + member).getBytes(StandardCharsets.UTF_8)),
                        new Point(-100 + pair * 50.0, 64, 0),
                        1.0,
                        10,
                        false,
                        6.0,
                        false));
            }
        }
        List<EnemyFact> enemies = List.of(
                new EnemyFact("minecraft:zombie", "west", new Point(-120, 64, 0), 1.0, 0, true, true, 10),
                new EnemyFact("minecraft:zombie", "east", new Point(120, 64, 0), 1.0, 0, true, true, 5));
        CoordinatorState state = new CoordinatorState(Optional.of(TeamDefenseScenarios.CENTER), 200, List.of(), 0);

        DefensePlan plan = DefensePlanner.plan(
                state, defenders, enemies, TeamDefenseScenarios.PROFILES, TeamDefenseScenarios.PROFILE);

        long west = plan.posts().stream()
                .filter(post -> Math.abs(post.post().x() + 100) < 1e-6)
                .count();
        long east = plan.posts().stream()
                .filter(post -> Math.abs(post.post().x() - 100) < 1e-6)
                .count();
        assertEquals(
                6,
                west,
                "К краю с десятью мобами идут три пары: две ближайшие помощницы и местная, иначе помощь неполная");
        assertEquals(
                4, east, "К краю с пятью мобами идут две пары: местная и одна помощница, иначе середина оголяется");
        assertTrue(
                WEIGHTS.localRadius() > 0,
                "Радиус боя положителен: иначе местные отряды не отличаются от резерва и план не сходится");
    }
}
