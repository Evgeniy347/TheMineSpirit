package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BoardTicketViewTests {
    @Test
    void tickets_SubmissionOrder_IsNotQueueOrder() {
        Board board = new Board(() -> 0L);
        submit(board, "first", 1);
        submit(board, "second", 50);

        assertEquals(
                List.of("first", "second"),
                board.tickets().stream().map(BoardTicket::id).toList(),
                "Неотсортированный вид отдаёт задания в порядке постановки: счёт открытых "
                        + "не платит за сортировку очереди на каждом раунде");
        assertEquals(
                List.of("second", "first"),
                board.all().stream().map(BoardTicket::id).toList(),
                "Очередь по-прежнему сортируется по приоритету: неотсортированный вид не подменяет её");
    }

    @Test
    void tickets_ReturnedView_DoesNotChangeBoard() {
        Board board = new Board(() -> 0L);
        submit(board, "only", 1);

        List<BoardTicket> view = board.tickets();

        assertThrows(
                UnsupportedOperationException.class,
                () -> view.add(new BoardTicket(
                        "other",
                        BoardCase.FILL_CRATER,
                        Optional.empty(),
                        1,
                        0L,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of())),
                "Вид доски только для чтения: иначе счётчик открытых заданий менял бы доску");
        assertEquals(
                List.of("only"),
                board.tickets().stream().map(BoardTicket::id).toList(),
                "Вид доски остаётся прежним после попытки изменить его извне");
    }

    private static void submit(Board board, String id, int priority) {
        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(new BoardTicket(
                        id,
                        BoardCase.FILL_CRATER,
                        Optional.empty(),
                        priority,
                        0L,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of())),
                "Постановка " + id + " принята доской");
    }
}
