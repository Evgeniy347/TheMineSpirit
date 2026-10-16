package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Откат провала: проваленное задание не переиздаётся до {@link BoardTuning#FAIL_BACKOFF_TICKS}.
 * Метка держится по id задания, а не по духу (урок ветки 32): одна метка на духа вытесняла бы
 * провал другого задания, и доска выдавала бы проваленное заново.
 */
class BoardBackoffTests {

    private static final UUID SPIRIT_ONE = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static BoardTicket supply(long createdAt) {
        return new BoardTicket("supply:dirt", BoardCase.SUPPLY_SITE, Optional.empty(),
                BoardRules.priorityOf(BoardCase.SUPPLY_SITE), createdAt, 6000, List.of(), Map.of());
    }

    @Test
    void submit_RecentlyFailedId_Backoff() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        board.submit(supply(0));
        assertEquals(BoardOutcome.CLAIMED, board.claim("supply:dirt", SPIRIT_ONE, 0),
                "Добыча выдана духу");
        assertTrue(board.fail("supply:dirt"), "Дух провалил добычу");
        board.prune(0);

        BoardOutcome again = board.submit(supply(0));

        assertTrue(again != BoardOutcome.ACCEPTED && board.find("supply:dirt").isEmpty(),
                "Проваленное задание не переиздаётся сразу: без отката доска ставила бы добычу "
                        + "каждую пересборку, а дух брал бы её в пустой склад снова и снова "
                        + "(исход " + again + ")");
    }

    @Test
    void submit_AfterBackoff_Accepted() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        board.submit(supply(0));
        board.claim("supply:dirt", SPIRIT_ONE, 0);
        board.fail("supply:dirt");

        now[0] = BoardTuning.FAIL_BACKOFF_TICKS;
        board.prune(now[0]);
        BoardOutcome again = board.submit(supply(0));

        assertEquals(BoardOutcome.ACCEPTED, again,
                "После отката задание снова ставится: доска не отказывается от добычи "
                        + "навсегда, а лишь даёт складу время наполниться");
    }
}
