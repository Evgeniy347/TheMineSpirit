package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            board.fail(ID, new StockView(Map.of()));
        }
        return board;
    }

    @Test
    void blockedLine_BlockedEntry_WaitReasonSameTextAsRest() {
        long[] now = {0};
        Board board = blockedBoard(now);
        BoardLogEntry entry = board.drainBlocks().get(0);

        String line = BoardNoticeText.line(board, entry, new StockView(Map.of()), now[0]);

        assertTrue(
                line.contains("waitReason=заблокировано после " + BoardTuning.ticketFailLimit() + " провалов"),
                "Строка BLOCKED обязана нести ту же причину, что REST: иначе grep по waitReason не находит "
                        + "заблокированное задание (пункт E отчёта); строка: " + line);
        assertFalse(line.contains("waitReason=—"), "Поле waitReason у BLOCKED больше не пустое: строка: " + line);
    }

    @Test
    void unblockedLine_UnblockedEntry_NamesOldReason() {
        long[] now = {0};
        Board board = blockedBoard(now);
        board.drainBlocks();

        board.claimable(new StockView(Map.of("dirt", 9)), now[0]);
        BoardLogEntry entry = board.drainBlocks().get(0);

        String line = BoardNoticeText.line(board, entry, new StockView(Map.of("dirt", 9)), now[0]);

        assertTrue(line.contains("id " + ID) || line.contains("задание=" + ID), "Строка называет задание: " + line);
        assertTrue(
                line.contains("старая причина:"),
                "При переиздании видно, из-за чего задание стояло: иначе новый createdAt выглядит как новое "
                        + "задание без истории; строка: " + line);
    }
}
