package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossActions.board;
import static com.theminespirit.core.board.graph.CrossActions.boardTicket;
import static com.theminespirit.core.board.graph.CrossActions.id;
import static com.theminespirit.core.board.graph.CrossScene.FED;
import static com.theminespirit.core.board.graph.CrossScene.facts;
import static com.theminespirit.core.board.graph.CrossScene.ids;
import static com.theminespirit.core.board.graph.CrossScene.named;
import static com.theminespirit.core.board.graph.CrossScene.plan;
import static com.theminespirit.core.board.graph.CrossScene.spirit;
import static com.theminespirit.core.board.graph.CrossScene.stageOne;
import static com.theminespirit.core.board.graph.CrossScene.task;
import static com.theminespirit.core.board.graph.CrossScene.threat;
import static com.theminespirit.core.board.graph.CrossScene.tickets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.SpiritView;
import com.theminespirit.core.movement.DisengageRoute;
import com.theminespirit.core.sleep.SleepFacts;
import com.theminespirit.core.sleep.SleepRule;
import com.theminespirit.core.tactics.Point;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CrossFleeScenarioTests {
    @Test
    void flee_WhileChopping_GraphKeepsChopAndRetreatWaits() {
        GraphWorld world = stageOne();
        world.with("oak_log", 0).with("oak_planks", 0);
        GraphFacts calm = facts(named(spirit("spirit-1"), spirit("spirit-2")));
        GraphTickets.Result calmTickets = tickets(world, calm);
        GraphTickets.Ticket chop = calmTickets.post().stream()
                .filter(ticket -> ticket.node().startsWith("chop_"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("В сцене нет рубки: " + calmTickets.post()));

        Board board = new Board(() -> 0);
        board.submit(boardTicket(chop));
        board.claim(chop.id(), id("spirit-1"), 0);

        GraphFacts danger = facts(
                named(spirit("spirit-1", 4, 20), spirit("spirit-2")),
                List.of(
                        threat("mob-a", "spirit-1", GraphFacts.TargetKind.SPIRIT, 1),
                        threat("mob-b", "spirit-1", GraphFacts.TargetKind.SPIRIT, 2)),
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                Map.of());
        GraphTickets.Result dangerTickets = tickets(world, danger);
        GraphTickets.Ticket retreat = dangerTickets.post().stream()
                .filter(ticket -> ticket.id().endsWith(":retreat"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Нет бегства в графе: " + dangerTickets.post()));
        board.submit(boardTicket(retreat));

        DispatchRound.Candidate chopper = new DispatchRound.Candidate(
                id("spirit-1"), "spirit-1", "chop_tree", List.of(), FED, SpiritView.free(), false, List.of());
        DispatchRound round = new DispatchRound(board, 0);
        Optional<String> refusal = round.refusal(boardTicket(retreat), chopper);

        assertTrue(
                dangerTickets.post().stream().anyMatch(ticket -> ticket.id().endsWith(":retreat")),
                "Граф ставит retreat даже при занятой рубке: "
                        + dangerTickets.post().stream()
                                .map(GraphTickets.Ticket::id)
                                .toList());
        assertTrue(
                dangerTickets.post().stream().anyMatch(ticket -> ticket.node().startsWith("chop_")),
                "Рубка не исчезает из графа при угрозе: " + dangerTickets.post());
        assertEquals(
                BoardStatus.ASSIGNED,
                board.statusOf(chop.id()).orElseThrow(),
                "Доска оставляет рубку за духом, метка работы не снимается");
        assertTrue(refusal.isPresent(), "Рубящий дух не берёт бегство вторым заданием: " + refusal);
        assertTrue(
                refusal.orElseThrow().contains("занят"),
                "Причина отказа называет занятость рубкой, а не молчит: " + refusal);
        assertTrue(
                round.choose(boardTicket(retreat), List.of(chopper)).isEmpty(),
                "Бегство ждёт, пока рубка не закончится: граф и доска не умеют её вытеснить");
    }

    @Test
    void flee_NightMobsAndExpedition_RetreatBeatsSleepAndFollow() {
        GraphWorld world = stageOne();
        world.with("oak_log", 0).with("oak_planks", 0).with("coal", 0);
        world.event("night").event("expedition_ready");
        GraphFacts facts = facts(
                named(spirit("spirit-1", 4, 20), spirit("spirit-2")),
                List.of(
                        threat("mob-a", "spirit-1", GraphFacts.TargetKind.SPIRIT, 1),
                        threat("mob-b", "spirit-1", GraphFacts.TargetKind.SPIRIT, 2)),
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                Map.of());
        GraphPlan plan = plan(world, facts);

        GraphPlan.Task defend = task(plan, "event:life_threat:defend");
        GraphPlan.Task retreat = task(plan, "event:life_threat:retreat");
        GraphPlan.Task sleep = task(plan, "event:life_threat:sleep");
        GraphPlan.Task expedition = task(plan, "event:expedition_ready:expedition");

        assertEquals(100, defend.priority(), "Ночные мобы поднимают защиту на потолок: " + ids(plan));
        assertTrue(defend.priority() > retreat.priority(), "Сначала бой, потом отход: " + ids(plan));
        assertTrue(retreat.priority() > sleep.priority(), "Отход от мобов важнее сна: " + ids(plan));
        assertTrue(retreat.priority() > expedition.priority(), "Отход важнее похода: " + ids(plan));
        assertTrue(
                SleepRule.timeToSleep(new SleepFacts(true, false, true, false)),
                "Ночью в поселении без опасности механика сна разрешает спать");
        assertFalse(
                SleepRule.timeToSleep(new SleepFacts(true, true, true, false)),
                "Под мобами механика сна спать не даёт, хотя задание sleep на доске есть");
        assertTrue(
                DisengageRoute.reached(new Point(-8, 64, 0), new Point(2, 64, 0), 8),
                "Точка отхода в восьми блоках от угрозы считается достигнутой");
        assertEquals(
                BoardCase.ACTIVITY_RETREAT,
                BoardActivities.caseOfScript("retreat").orElseThrow(),
                "Бегство выполняется действием retreat");
    }
}
