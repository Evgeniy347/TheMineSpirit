package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FailureTargetsTests {
    @Test
    void choose_FirstTargetUnreachable_TakesAnotherTarget() {
        BlockCoord dead = new BlockCoord(-179, 71, -503);
        BlockCoord other = new BlockCoord(-150, 70, -480);

        assertEquals(
                other,
                FailureTargets.choose(List.of(dead, other), Set.of(FailureTargets.key(dead)), 6),
                "Недостижимый ствол пропускают: рубка переходит к другому дереву того же узла");
    }

    @Test
    void choose_TargetNearAnotherDeadOne_IsTreatedAsSameTree() {
        BlockCoord dead = new BlockCoord(-179, 71, -503);
        BlockCoord sameTree = new BlockCoord(-177, 73, -501);

        assertEquals(
                sameTree,
                FailureTargets.choose(List.of(sameTree), Set.of(FailureTargets.key(dead)), 6),
                "Одна цель внутри радиуса считается той же: без запасной цели честно повторяют её");
    }

    @Test
    void choose_AllTargetsUnreachable_FallsBackToFirst() {
        BlockCoord dead = new BlockCoord(0, 64, 0);

        assertEquals(
                dead,
                FailureTargets.choose(List.of(dead), Set.of(FailureTargets.key(dead)), 6),
                "Если недостижимы все цели, узел не остаётся без работы молча: он честно повторяет ближайшую, "
                        + "а вес узла решает порог разных целей");
    }

    @Test
    void choose_DistantDeadTarget_DoesNotBlockCandidate() {
        BlockCoord candidate = new BlockCoord(10, 64, 10);

        assertEquals(
                candidate,
                FailureTargets.choose(List.of(candidate), Set.of("10,64,40"), 6),
                "Провал далёкой цели не исключает эту: исключение адресное");
    }

    @Test
    void chop_UnreachableJungleTree_KeepsNodeAndPicksAnotherTree() {
        BlockCoord dead = new BlockCoord(-179, 71, -503);
        BlockCoord other = new BlockCoord(-150, 70, -480);
        GraphWorld world = GraphWorld.settled();
        GraphTickets.Result calm = AllocationCases.tickets(world);
        int calmPriority = AllocationCases.only(calm, "chop_jungle_log").stream()
                .findFirst()
                .orElseThrow()
                .task()
                .priority();

        world.facts(world.facts.withFeedback(new GraphFeedback(
                GraphFeedback.Clock.unknown(),
                GraphFeedback.Player.none(),
                List.of(new GraphFeedback.Failure(
                        "chop_jungle_log", FailureTargets.key(dead), 5, GraphFeedback.Cause.NO_PATH, 100)))));
        GraphTickets.Result failed = AllocationCases.tickets(world);
        int failedPriority = AllocationCases.only(failed, "chop_jungle_log").stream()
                .findFirst()
                .orElseThrow()
                .task()
                .priority();

        assertEquals(
                calmPriority,
                failedPriority,
                "Один недоступный ствол не снимает рубку с доски: узел сохраняет вес и ищет другое дерево");
        assertEquals(
                other,
                FailureTargets.choose(List.of(dead, other), Set.of(FailureTargets.key(dead)), 6),
                "Доска ставит рубку по другой цели того же узла");
    }

    @Test
    void near_MalformedKey_IsIgnored() {
        BlockCoord candidate = new BlockCoord(10, 64, 10);

        assertFalse(
                FailureTargets.near(candidate, Set.of("не координата", ""), 6),
                "Испорченный ключ памяти не исключает цель: лучше повторить, чем остаться без работы");
        assertTrue(FailureTargets.near(candidate, Set.of("10,64,10"), 0), "Нулевой радиус исключает ровно точную цель");
    }
}
