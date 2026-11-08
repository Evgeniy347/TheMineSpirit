package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossActions.*;
import static com.theminespirit.core.board.graph.CrossScene.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.SpiritView;
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
}
