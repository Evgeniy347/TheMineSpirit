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

class BoardNoticeTextTests {
    private static final String ID = "first:build_mine";
    private static final BoardCase CASE = BoardCase.BUILD_MINE;

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

    private static UUID spirit(int number) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", number));
    }

    private static Board blockedBoard(long[] now) {
        Board board = new Board(() -> now[0]);
        for (int attempt = 1; attempt <= BoardTuning.ticketFailLimit(); attempt++) {
            if (attempt > 1) {
                board.prune(now[0]);
                now[0] += BoardTuning.failBackoffTicks();
            }
            board.submit(ticket());
            board.claim(ID, spirit(attempt), now[0]);
            board.fail(ID, new StockView(Map.of()), null);
        }
        return board;
    }

    @Test
    void blockedLine_BlockedEntry_WaitReasonSameTextAsRest() {
        long[] now = {0};
        Board board = blockedBoard(now);
        BoardLogEntry entry = board.failures().drainBlocks().get(0);

        String line = BoardNoticeText.line(board, entry, new StockView(Map.of()), now[0]);

        assertTrue(
                line.contains("waitReason=заблокировано после " + BoardTuning.ticketFailLimit() + " провалов"),
                "Строка BLOCKED обязана нести ту же причину, что REST: иначе grep по waitReason не находит "
                        + "заблокированное задание (пункт E отчёта); строка: " + line
                        + " При смене blockedLine_BlockedEntry_WaitReasonSameTextAsRest проверь это утверждение.");
        assertFalse(
                line.contains("waitReason=—"),
                "Поле waitReason у BLOCKED больше не пустое: строка: " + line
                        + " Иначе тест blockedLine_BlockedEntry_WaitReasonSameTextAsRest перестанет ловить эту поломку. При смене blockedLine_BlockedEntry_WaitReasonSameTextAsRest проверь это утверждение.");
    }

    @Test
    void blockedLine_HistoryInReason_IsNotRepeatedAsSecondField() {
        long[] now = {0};
        Board board = blockedBoard(now);
        BoardLogEntry entry = board.failures().drainBlocks().get(0);

        String line = BoardNoticeText.line(board, entry, new StockView(Map.of()), now[0]);

        assertFalse(
                line.contains("история="),
                "История провалов уже названа в причине (history=); второй раз в строке BLOCKED она раздувает "
                        + "инцидент до килобайтов: " + line
                        + " Иначе тест blockedLine_HistoryInReason_IsNotRepeatedAsSecondField перестанет ловить эту поломку. При смене blockedLine_HistoryInReason_IsNotRepeatedAsSecondField проверь это утверждение.");
        assertEquals(
                1,
                line.split("history=", -1).length - 1,
                "История провалов печатается ровно один раз: дубль мешает читать лог: " + line
                        + " Иначе тест blockedLine_HistoryInReason_IsNotRepeatedAsSecondField перестанет ловить эту поломку. При смене blockedLine_HistoryInReason_IsNotRepeatedAsSecondField проверь это утверждение.");
    }

    @Test
    void unblockedLine_UnblockedEntry_NamesOldReason() {
        long[] now = {0};
        Board board = blockedBoard(now);
        board.failures().drainBlocks();

        board.claimable(new StockView(Map.of("dirt", 9)), now[0]);
        BoardLogEntry entry = board.failures().drainBlocks().get(0);

        String line = BoardNoticeText.line(board, entry, new StockView(Map.of("dirt", 9)), now[0]);

        assertTrue(
                line.contains("id " + ID) || line.contains("задание=" + ID),
                "Строка называет задание: " + line
                        + " Иначе тест unblockedLine_UnblockedEntry_NamesOldReason перестанет ловить эту поломку. При смене unblockedLine_UnblockedEntry_NamesOldReason проверь это утверждение.");
        assertTrue(
                line.contains("старая причина:"),
                "При переиздании видно, из-за чего задание стояло: иначе новый createdAt выглядит как новое "
                        + "задание без истории; строка: " + line
                        + " При смене unblockedLine_UnblockedEntry_NamesOldReason проверь это утверждение.");
    }
}
