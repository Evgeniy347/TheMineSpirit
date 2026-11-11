package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardBlockNeedTests {
    private static final BoardCase CASE = BoardCase.STOCK_CHESTS;
    private static final String ID = "first:stock_chests";

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

    @Test
    void undoFailure_WithPath_BlocksUntilLeaseKeyChanges() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        BoardTicket same = undo("graph:event:undo_lease:undo:lease-1#10,70,10:3");
        board.submit(same);
        board.claim(same.id(), spirit(1), now[0]);
        board.fail(same.id(), new StockView(Map.of()), "нет подхода", new GraphBlock.Path("10,70,10"));

        assertEquals(
                Optional.of(BoardStatus.BLOCKED),
                board.statusOf(same.id()),
                "Провал отката по пути — блокировка с ключом, а не FAILED: иначе доска забудет задание и граф "
                        + "поставит его заново");
        assertTrue(
                board.failures().graphBlocks().stream().anyMatch(block -> block.need() instanceof GraphBlock.Path),
                "Ключ блокировки отката — путь: " + board.failures().graphBlocks());
        board.prune(now[0] + BoardTuning.failBackoffTicks() * 10);
        assertEquals(
                Optional.of(BoardStatus.BLOCKED),
                board.statusOf(same.id()),
                "Блокировка по ключу не снимается таймером: повтор только после изменения ключа");
        assertEquals(
                BoardOutcome.DUPLICATE,
                board.submit(same),
                "Тот же ключ аренды — то же задание: граф не шумит новой выдачей");

        BoardTicket changed = undo("graph:event:undo_lease:undo:lease-1#10,69,10:2");
        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(changed),
                "Откатанная клетка меняет ключ аренды — откат выдаётся снова");
    }

    private static BoardTicket undo(String id) {
        return new BoardTicket(
                id,
                BoardCase.ACTIVITY_UNDO,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.ACTIVITY_UNDO),
                0,
                1,
                id,
                List.of(),
                Map.of());
    }

    private static UUID spirit(int number) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", number));
    }

    @Test
    void snapshot_FailuresReachLimit_CarriesTypedNeed() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        for (int attempt = 1; attempt <= BoardTuning.ticketFailLimit(); attempt++) {
            if (attempt > 1) {
                board.prune(now[0]);
                now[0] += BoardTuning.failBackoffTicks();
            }
            board.submit(ticket());
            board.claim(ID, spirit(attempt), now[0]);
            board.fail(ID);
        }

        List<BoardState.Blocked> blocked = board.snapshot().blocked();
        assertEquals(1, blocked.size(), "Одна блокировка — одна запись условия");
        assertTrue(
                blocked.get(0).block() != null && blocked.get(0).block().need() != null,
                "Блокировка без типизированной нужды не собирается: графу нечего ждать и нечего поднимать");
    }

    @Test
    void blockNote_CarriesTypedNeed_EvenWhenNoItemShortage() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        board.submit(ticket());
        board.claim(ID, spirit(1), now[0]);
        board.fail(
                ID,
                new StockView(Map.of()),
                "нет подхода",
                new GraphBlock.Tool("pickaxe", 0, spirit(1).toString()));

        List<BoardLogEntry> blocks = board.failures().drainBlocks();

        assertEquals(1, blocks.size(), "Одна блокировка — одна запись журнала");
        assertTrue(
                blocks.get(0).modifiers().stream().anyMatch(line -> line.startsWith("need=")),
                "Запись BLOCKED называет ключ блокировки, даже когда missing пуст: иначе в живом журнале видно "
                        + "«BLOCKED без missing» и непонятно, что снимет блокировку: "
                        + blocks.get(0).modifiers());
    }

    @Test
    void restore_LegacyBlockedWithoutNeed_ReturnsToPending() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        board.submit(ticket());
        BoardTicket stored = board.find(ID).orElseThrow();
        BoardState legacy = new BoardState(
                List.of(new BoardState.Entry(stored, BoardStatus.BLOCKED, null, null, BoardTuning.ticketFailLimit())),
                Set.of(),
                List.of(new BoardState.Blocked(ID, Map.of("dirt", 9), Map.of("dirt", 0), now[0], null)),
                List.of(),
                List.of());

        board.restore(legacy);

        assertEquals(
                Optional.of(BoardStatus.PENDING),
                board.statusOf(ID),
                "Старая блокировка без Need не висит вечно: условие пересчитывается заново, а не ждёт пустоту");
    }
}
