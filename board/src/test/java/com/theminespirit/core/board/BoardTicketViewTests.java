package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
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
                        + "не платит за сортировку очереди на каждом раунде"
                        + " Иначе тест tickets_SubmissionOrder_IsNotQueueOrder перестанет ловить эту поломку. При смене tickets_SubmissionOrder_IsNotQueueOrder проверь это утверждение.");
        assertEquals(
                List.of("second", "first"),
                board.all().stream().map(BoardTicket::id).toList(),
                "Очередь по-прежнему сортируется по приоритету: неотсортированный вид не подменяет её"
                        + " Иначе тест tickets_SubmissionOrder_IsNotQueueOrder перестанет ловить эту поломку. При смене tickets_SubmissionOrder_IsNotQueueOrder проверь это утверждение.");
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
                "Вид доски только для чтения: иначе счётчик открытых заданий менял бы доску"
                        + " При смене tickets_ReturnedView_DoesNotChangeBoard проверь это утверждение.");
        assertEquals(
                List.of("only"),
                board.tickets().stream().map(BoardTicket::id).toList(),
                "Вид доски остаётся прежним после попытки изменить его извне"
                        + " Иначе тест tickets_ReturnedView_DoesNotChangeBoard перестанет ловить эту поломку. При смене tickets_ReturnedView_DoesNotChangeBoard проверь это утверждение.");
    }

    @Test
    void iterationViews_UnsortedQueue_DoNotPayForOrder() {
        AtomicLong clockReads = new AtomicLong();
        Board board = new Board(clockReads::getAndIncrement);
        submit(board, "first", 1);
        submit(board, "second", 50);
        clockReads.set(0);

        BoardReserve.items(board);
        BoardStages.held(board);
        TransferCarriers.carrying(board, "craft", Set.of());
        TransferCarriers.awaitsDelivery(board, "craft");
        TransferCarriers.planned(board, "craft");
        VitalChain.withPath(Map.of("food", "hungry"), board, UUID.randomUUID());
        new AbsentOwners().sweep(board, Set.of(), 0);

        assertEquals(
                0,
                clockReads.get(),
                "Перебор заданий не сортирует очередь: очередь сортирует только all(), а перебор берёт "
                        + "tickets() — иначе каждый цикл платит за порядок, который ему не нужен"
                        + " При смене iterationViews_UnsortedQueue_DoNotPayForOrder проверь это утверждение.");
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
                "Постановка " + id + " принята доской"
                        + " Иначе тест submit перестанет ловить эту поломку. При смене submit проверь это утверждение. Иначе поломка пройдёт незамеченной в прогоне.");
    }
}
