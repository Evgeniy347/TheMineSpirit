package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
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
                Map.of("dirt", 9));
    }

    private static long blocked(Board board, long[] now) {
        for (int attempt = 0; attempt < BoardTuning.ticketFailLimit(); attempt++) {
            board.submit(ticket());
            assertEquals(
                    BoardOutcome.CLAIMED,
                    board.claim(ID, SPIRITS[attempt], now[0]),
                    "Попытка " + (attempt + 1) + " выдана: иначе провал не к чему привязать"
                            + " При смене механику проверь это утверждение. Иначе поломка пройдёт незамеченной в прогоне.");
            assertTrue(
                    board.fail(ID),
                    "Попытка " + (attempt + 1) + " провалена"
                            + " Иначе тест механику перестанет ловить эту поломку. При смене механику проверь это утверждение. Иначе поломка пройдёт незамеченной в прогоне.");
            now[0] += BoardTuning.failBackoffTicks();
        }
        assertEquals(
                BoardStatus.BLOCKED,
                board.statusOf(ID).orElse(null),
                "После " + BoardTuning.ticketFailLimit() + " провалов задание блокируется: это состояние "
                        + "живой шахты в observe9"
                        + " Иначе тест механику перестанет ловить эту поломку. При смене механику проверь это утверждение.");
        return now[0];
    }

    @Test
    void unblock_BlockedTicket_ClearsFailuresAndReturnsPending() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        blocked(board, now);

        assertTrue(
                board.failures().unblock(ID, now[0]),
                "Ручная разблокировка снимает BLOCKED: иначе оператор не сможет вернуть задание после "
                        + "исправления условия. При смене unblock_BlockedTicket_ClearsFailuresAndReturnsPending "
                        + "проверь BoardFailureDesk.unblock и REST /unblock.");
        assertEquals(
                BoardStatus.PENDING,
                board.statusOf(ID).orElse(null),
                "После ручной разблокировки билет ждёт исполнителя: иначе работа останется выключенной. "
                        + "При смене unblock_BlockedTicket_ClearsFailuresAndReturnsPending проверь BlockedBook.release.");
        assertEquals(
                0,
                board.ticketFailures(ID),
                "Счётчик провалов обнуляется: иначе следующий отказ снова мгновенно заблокирует исправленное "
                        + "задание. При смене unblock_BlockedTicket_ClearsFailuresAndReturnsPending проверь очистку failures.");
        assertFalse(
                board.failures().unblock(ID, now[0]),
                "Повторная разблокировка не создаёт второе событие: иначе журнал заполнится ложными UNBLOCKED. "
                        + "При смене unblock_BlockedTicket_ClearsFailuresAndReturnsPending проверь проверку статуса BLOCKED.");
    }

    @Test
    void blocked_StockAppears_UnblocksByConditionNotByTime() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        long blockedAt = blocked(board, now);

        now[0] = blockedAt + BoardTuning.caseFailPauseCapTicks();
        board.claimable(new StockView(Map.of()), now[0]);
        assertEquals(
                BoardStatus.BLOCKED,
                board.statusOf(ID).orElse(null),
                "Таймер больше не снимает блокировку: без изменения условия задание остаётся BLOCKED"
                        + " Иначе тест blocked_StockAppears_UnblocksByConditionNotByTime перестанет ловить эту поломку. При смене blocked_StockAppears_UnblocksByConditionNotByTime проверь это утверждение.");

        board.claimable(new StockView(Map.of("dirt", 9)), now[0]);
        assertEquals(
                BoardStatus.PENDING,
                board.statusOf(ID).orElse(null),
                "Предмет появился — условие снято, задание снова доступно"
                        + " Иначе тест blocked_StockAppears_UnblocksByConditionNotByTime перестанет ловить эту поломку. При смене blocked_StockAppears_UnblocksByConditionNotByTime проверь это утверждение.");
        assertTrue(
                board.failures().drainBlocks().stream()
                        .anyMatch(entry -> entry.event() == BoardLog.Event.UNBLOCKED
                                && entry.modifiers().stream().anyMatch(text -> "changed=dirt:9->0".equals(text))),
                "Разблокировка называет изменение запаса, а не таймаут"
                        + " Иначе тест blocked_StockAppears_UnblocksByConditionNotByTime перестанет ловить эту поломку. При смене blocked_StockAppears_UnblocksByConditionNotByTime проверь это утверждение.");
        assertTrue(
                board.failures().drainBlocks().isEmpty(),
                "Запись отдаётся один раз"
                        + " Иначе тест blocked_StockAppears_UnblocksByConditionNotByTime перестанет ловить эту поломку. При смене blocked_StockAppears_UnblocksByConditionNotByTime проверь это утверждение.");
    }
}
