package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DispatchPreemptionTests {
    private static final EatNeed FED = new EatNeed(20, 20, 20, 20);

    @Test
    void choose_RetreatAboveWork_ReturnsWorkToBoard() {
        UUID spirit = new UUID(0, 1);
        BoardTicket chop = new BoardTicket(
                "graph:chop_oak_log",
                BoardCase.GATHER_MATERIALS,
                Optional.of(spirit),
                60,
                0,
                1,
                "graph:chop_oak_log",
                List.of(),
                Map.of());
        BoardTicket retreat = retreat(100);
        Board board = board(List.of(chop));
        board.claim(chop.id(), spirit, 0);
        DispatchRound round = new DispatchRound(board, 0);
        DispatchRound.Candidate chopper =
                new DispatchRound.Candidate(spirit, "spirit-1", "chop_tree", List.of(), FED, SpiritView.free());

        Optional<DispatchRound.Candidate> chosen = round.choose(retreat, List.of(chopper));

        assertTrue(chosen.isPresent(), "Жизнь выше работы: рубящий дух берёт бегство, а не ждёт конца рубки");
        assertEquals(
                List.of(chop.id()),
                round.drainDisplaced(spirit).stream().map(BoardTicket::id).toList(),
                "Вытесненная работа названа, чтобы мод остановил действие духа");
        assertEquals(BoardStatus.PENDING, board.statusOf(chop.id()).orElseThrow(), "Работа вернулась на доску");
        assertTrue(round.drainDisplaced(spirit).isEmpty(), "Снятую работу отдают один раз");
    }

    @Test
    void choose_EqualHeldLife_DoesNotPreempt() {
        UUID spirit = new UUID(0, 1);
        BoardTicket defend = new BoardTicket(
                "graph:defend",
                BoardCase.ACTIVITY_DEFEND,
                Optional.of(spirit),
                100,
                0,
                1,
                "graph:defend",
                List.of(),
                Map.of());
        Board board = board(List.of(defend));
        board.claim(defend.id(), spirit, 0);
        DispatchRound round = new DispatchRound(board, 0);
        DispatchRound.Candidate fighter =
                new DispatchRound.Candidate(spirit, "spirit-1", "defend", List.of(), FED, SpiritView.free());

        assertTrue(
                round.choose(retreat(100), List.of(fighter)).isEmpty(),
                "Бегство не вытесняет равную по весу защиту: дерущийся дух не бросает бой без причины");
        assertEquals(
                BoardStatus.ASSIGNED,
                board.statusOf(defend.id()).orElseThrow(),
                "Защита остаётся за духом: доска не снимает равную работу");
        assertTrue(round.drainDisplaced(spirit).isEmpty(), "Ничего не вытеснено");
    }

    @Test
    void choose_NormalWorkWhileChopping_WaitsForEnd() {
        UUID spirit = new UUID(0, 1);
        BoardTicket chop = new BoardTicket(
                "graph:chop_oak_log",
                BoardCase.GATHER_MATERIALS,
                Optional.of(spirit),
                60,
                0,
                1,
                "graph:chop_oak_log",
                List.of(),
                Map.of());
        BoardTicket mine = new BoardTicket(
                "graph:mine_coal",
                BoardCase.GATHER_MATERIALS,
                Optional.empty(),
                50,
                0,
                1,
                "graph:mine_coal",
                List.of(),
                Map.of());
        Board board = board(List.of(chop));
        board.claim(chop.id(), spirit, 0);
        DispatchRound round = new DispatchRound(board, 0);
        DispatchRound.Candidate chopper =
                new DispatchRound.Candidate(spirit, "spirit-1", "chop_tree", List.of(), FED, SpiritView.free());

        assertTrue(
                round.choose(mine, List.of(chopper)).isEmpty(),
                "Обычная работа не вытесняет занятого духа: иначе дух метался бы между задачами");
        assertEquals(
                BoardStatus.ASSIGNED,
                board.statusOf(chop.id()).orElseThrow(),
                "Рубка остаётся за духом: обычная работа ждёт");
    }

    private static BoardTicket retreat(int priority) {
        return new BoardTicket(
                "graph:retreat",
                BoardCase.ACTIVITY_RETREAT,
                Optional.empty(),
                priority,
                0,
                1,
                "graph:retreat",
                List.of(),
                Map.of());
    }

    private static Board board(List<BoardTicket> tickets) {
        Board board = new Board(() -> 0);
        tickets.forEach(board::submit);
        return board;
    }
}
