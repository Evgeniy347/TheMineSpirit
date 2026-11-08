package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossActions.*;
import static com.theminespirit.core.board.graph.CrossScene.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.SpiritView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CrossFleeScenarioTests {
    @Test
    void flee_WhileChopping_RetreatPreemptsChopAndChopReturnsToBoard() {
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
        board.submit(boardTicket(retreat, Optional.of(id("spirit-1"))));

        DispatchRound.Candidate chopper = new DispatchRound.Candidate(
                id("spirit-1"), "spirit-1", "chop_tree", List.of(), FED, SpiritView.free(), false, List.of());
        DispatchRound round = new DispatchRound(board, 0);

        assertTrue(
                dangerTickets.post().stream().anyMatch(ticket -> ticket.id().endsWith(":retreat")),
                "Граф ставит retreat даже при занятой рубке: "
                        + dangerTickets.post().stream()
                                .map(GraphTickets.Ticket::id)
                                .toList());
        assertTrue(
                dangerTickets.post().stream().anyMatch(ticket -> ticket.node().startsWith("chop_")),
                "Рубка не исчезает из графа при угрозе: " + dangerTickets.post());
        assertTrue(
                round.choose(boardTicket(retreat, Optional.of(id("spirit-1"))), List.of(chopper))
                        .isPresent(),
                "Бегство выше работы: рубящий дух бросает дерево и уходит от мобов");
        assertEquals(
                List.of(chop.id()),
                round.drainDisplaced(id("spirit-1")).stream()
                        .map(BoardTicket::id)
                        .toList(),
                "Вытесненная рубка возвращается на доску, а не пропадает");
        assertEquals(
                BoardStatus.PENDING,
                board.statusOf(chop.id()).orElseThrow(),
                "Рубка больше не держится за убегающим духом: её возьмёт другой или он сам после бегства");
        assertTrue(
                round.refusal(boardTicket(retreat, Optional.of(id("spirit-1"))), chopper)
                        .isEmpty(),
                "После вытеснения дух свободен для бегства: "
                        + round.refusal(boardTicket(retreat, Optional.of(id("spirit-1"))), chopper));
    }
}
