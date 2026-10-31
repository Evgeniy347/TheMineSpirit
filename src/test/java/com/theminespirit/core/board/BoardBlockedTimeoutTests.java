package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardBlockedTimeoutTests {
    private static final BoardCase CASE = BoardCase.BUILD_MINE;
    private static final String ID = "first:build_mine";
    private static final UUID[] SPIRITS = {
        UUID.fromString("10000000-0000-0000-0000-000000000001"),
        UUID.fromString("10000000-0000-0000-0000-000000000002"),
        UUID.fromString("10000000-0000-0000-0000-000000000003"),
        UUID.fromString("10000000-0000-0000-0000-000000000004"),
        UUID.fromString("10000000-0000-0000-0000-000000000005"),
    };

    private static BoardTicket ticket() {
        return new BoardTicket(
                ID,
                CASE,
                Optional.empty(),
                BoardRules.priorityOf(CASE),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
    }

    private static long blocked(Board board, long[] now) {
        for (int attempt = 0; attempt < BoardTuning.ticketFailLimit(); attempt++) {
            board.submit(ticket());
            assertEquals(
                    BoardOutcome.CLAIMED,
                    board.claim(ID, SPIRITS[attempt], now[0]),
                    "Попытка " + (attempt + 1) + " выдана: иначе провал не к чему привязать");
            assertTrue(board.fail(ID), "Попытка " + (attempt + 1) + " провалена");
            now[0] += BoardTuning.failBackoffTicks();
        }
        assertEquals(
                BoardStatus.BLOCKED,
                board.statusOf(ID).orElse(null),
                "После " + BoardTuning.ticketFailLimit() + " провалов задание блокируется: это состояние "
                        + "живой шахты в observe9");
        return now[0];
    }

    @Test
    void blocked_StockUnchanged_UnblocksByTimeout() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        long blockedAt = blocked(board, now);
        now[0] = blockedAt;
        board.claimable(new StockView(Map.of()), now[0]);
        assertEquals(
                BoardStatus.BLOCKED,
                board.statusOf(ID).orElse(null),
                "Без смены запасов до таймаута задание остаётся BLOCKED: иначе оно крутит один и тот же провал");

        now[0] = blockedAt + BoardTuning.caseFailPauseCapTicks();
        board.claimable(new StockView(Map.of()), now[0]);
        assertNotEquals(
                BoardStatus.BLOCKED,
                board.statusOf(ID).orElse(null),
                "По таймауту BLOCKED снимается само: иначе заблокированная шахта стоит вечно, а город "
                        + "не двигается (observe9: DONE 1243 не растёт, все духи idle)");
        assertTrue(
                board.drainBlocks().stream()
                        .anyMatch(entry -> entry.event() == BoardLog.Event.UNBLOCKED
                                && entry.modifiers().stream().anyMatch(text -> text.startsWith("timeout="))),
                "Разблокировка по времени пишет в журнал причину timeout=: без неё не отличить таймаут "
                        + "от пополнения запасов");
    }
}
