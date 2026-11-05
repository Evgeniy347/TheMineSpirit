package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GraphTicketRepeatTests {
    private static final String LIVE_BOARD = "/live/observe23-dirt/board.json";
    private static final UUID SPIRIT = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static BoardTicket ticket(String id) {
        return new BoardTicket(
                id, BoardCase.GATHER_MATERIALS, Optional.empty(), 48, 29445699, 1, id, List.of(), Map.of());
    }

    @Test
    void liveObserve23_FinishedDirtWay_RepeatsAsNewRun() throws IOException {
        Board board = LiveBoardFixture.load(LIVE_BOARD);
        assertEquals(
                BoardStatus.DONE,
                board.statusOf("graph:dig_dirt").orElseThrow(),
                "Живая доска observe23: добыча земли закрыта и больше не выдавалась");

        assertTrue(
                board.reopen("graph:dig_dirt"),
                "Граф снова просит землю — закрытая добыча открывается заново, иначе источник земли молчит навсегда");

        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(ticket("graph:dig_dirt")),
                "После переоткрытия граф ставит новую добычу земли, а не получает дубликат старой");
        assertEquals(
                BoardStatus.PENDING,
                board.statusOf("graph:dig_dirt").orElseThrow(),
                "Новая добыча земли снова в очереди доски");
    }

    @Test
    void submit_SameDoneTicket_StaysDone() {
        Board board = new Board(() -> 100);
        BoardTicket backfill = new BoardTicket(
                "world:backfill:-1,2,-3",
                BoardCase.FILL_CRATER,
                Optional.empty(),
                42,
                0,
                1,
                "graph:event:backfill:backfill",
                List.of(),
                Map.of("dirt", 4));
        assertEquals(BoardOutcome.ACCEPTED, board.submit(backfill), "Засыпка ставится в очередь");
        board.claim(backfill.id(), SPIRIT, 0);
        board.complete(backfill.id());

        assertEquals(
                BoardOutcome.DUPLICATE,
                board.submit(backfill),
                "Повтор без переоткрытия остаётся дубликатом: сданное задание не откатывается в очередь само");
    }
}
