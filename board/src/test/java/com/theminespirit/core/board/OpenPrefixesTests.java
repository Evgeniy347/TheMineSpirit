package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OpenPrefixesTests {
    private static final String BACKFILL = "world:backfill:";
    private static final String TRACE = "world:trace:";
    private static final List<String> PREFIXES = List.of(BACKFILL, TRACE);
    private static final UUID SPIRIT = new UUID(0, 1);

    @Test
    void of_OpenTickets_CountsEachTrackedPrefix() {
        Board board = board();
        submit(board, "world:backfill:1,64,2");
        submit(board, "world:backfill:3,64,4");
        submit(board, "world:trace:5,64,6");
        submit(board, "graph:hut");

        OpenPrefixes open = OpenPrefixes.of(board, PREFIXES);

        assertEquals(2, open.open(BACKFILL), "Две засыпки в работе — порция уборки считает обе");
        assertEquals(1, open.open(TRACE), "След считается отдельно от ям: у них разные порции");
    }

    @Test
    void of_DoneAndCancelledTickets_DoNotTakeBatchSlots() {
        Board board = board();
        submit(board, "world:backfill:1,64,2");
        submit(board, "world:backfill:3,64,4");
        submit(board, "world:trace:5,64,6");
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim("world:backfill:1,64,2", SPIRIT, 0L),
                "Засыпку выдаёт дух: без выдачи завершить её нельзя");
        assertTrue(board.complete("world:backfill:1,64,2"), "Завершение задачи доски принято: билет был выдан духу");
        board.cancel("world:trace:5,64,6");

        OpenPrefixes open = OpenPrefixes.of(board, PREFIXES);

        assertEquals(
                1, open.open(BACKFILL), "Выполненная засыпка освобождает место порции: иначе порция кончится навсегда");
        assertEquals(0, open.open(TRACE), "Отменённый след не занимает место: иначе новые следы не поставить");
    }

    @Test
    void of_SimilarId_DoesNotMatchPrefix() {
        Board board = board();
        submit(board, "world:traceX:1,64,2");
        submit(board, "world:trace:1,64,2");

        OpenPrefixes open = OpenPrefixes.of(board, PREFIXES);

        assertEquals(1, open.open(TRACE), "Похожий id не считается следом: префикс задаёт начало, а не вхождение");
    }

    @Test
    void posted_AfterOurOwnSubmission_AddsSlotWithoutRescan() {
        Board board = board();
        submit(board, "world:backfill:1,64,2");
        OpenPrefixes open = OpenPrefixes.of(board, PREFIXES);

        open.posted("world:backfill:9,64,9");
        open.posted("graph:hut");

        assertEquals(
                2, open.open(BACKFILL), "Своя постановка сразу занимает место: следующая порция не перепостит лишнее");
        assertEquals(0, open.open(TRACE), "Чужой префикс счёт не сдвигает");
    }

    private static Board board() {
        return new Board(() -> 0L);
    }

    private static void submit(Board board, String id) {
        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(new BoardTicket(
                        id,
                        BoardCase.FILL_CRATER,
                        Optional.empty(),
                        1,
                        0L,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of())),
                "Постановка " + id + " принята доской");
    }
}
