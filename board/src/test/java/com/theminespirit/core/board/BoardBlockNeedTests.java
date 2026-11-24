package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
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
        assertEquals(
                1,
                blocked.size(),
                "Одна блокировка — одна запись условия"
                        + " Иначе тест snapshot_FailuresReachLimit_CarriesTypedNeed перестанет ловить эту поломку. При смене snapshot_FailuresReachLimit_CarriesTypedNeed проверь это утверждение.");
        assertTrue(
                blocked.get(0).block() != null && blocked.get(0).block().need() != null,
                "Блокировка без типизированной нужды не собирается: графу нечего ждать и нечего поднимать"
                        + " Иначе тест snapshot_FailuresReachLimit_CarriesTypedNeed перестанет ловить эту поломку. При смене snapshot_FailuresReachLimit_CarriesTypedNeed проверь это утверждение.");
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

        assertEquals(
                1,
                blocks.size(),
                "Одна блокировка — одна запись журнала"
                        + " Иначе тест blockNote_CarriesTypedNeed_EvenWhenNoItemShortage перестанет ловить эту поломку. При смене blockNote_CarriesTypedNeed_EvenWhenNoItemShortage проверь это утверждение.");
        assertTrue(
                blocks.get(0).modifiers().stream().anyMatch(line -> line.startsWith("need=")),
                "Запись BLOCKED называет ключ блокировки, даже когда missing пуст: иначе в живом журнале видно "
                        + "«BLOCKED без missing» и непонятно, что снимет блокировку: "
                        + blocks.get(0).modifiers()
                        + " При смене blockNote_CarriesTypedNeed_EvenWhenNoItemShortage проверь это утверждение.");
    }

    @Test
    void claimable_NamespacedNeed_UnblocksWithShortStock() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        board.submit(ticket());
        board.claim(ID, spirit(1), now[0]);
        board.fail(ID, new StockView(Map.of("dirt", 9)), "нет подхода", new GraphBlock.Item("minecraft:dirt", 9));

        assertEquals(
                Optional.of(BoardStatus.BLOCKED),
                board.statusOf(ID),
                "Провал с именованной нуждой блокирует задание"
                        + " Иначе тест claimable_NamespacedNeed_UnblocksWithShortStock перестанет ловить эту поломку. При смене claimable_NamespacedNeed_UnblocksWithShortStock проверь это утверждение.");

        board.claimable(new StockView(Map.of("dirt", 9)), now[0]);

        assertEquals(
                Optional.of(BoardStatus.PENDING),
                board.statusOf(ID),
                "Запас 'dirt' снимает блокировку 'minecraft:dirt': имена с неймспейсом и без — один предмет"
                        + " Иначе тест claimable_NamespacedNeed_UnblocksWithShortStock перестанет ловить эту поломку. При смене claimable_NamespacedNeed_UnblocksWithShortStock проверь это утверждение.");
    }

    @Test
    void restore_BlockedWithoutTicket_IsPruned() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        BoardState stale = new BoardState(
                List.of(),
                Set.of(),
                List.of(new BoardState.Blocked(
                        "world:plan_breach:gone",
                        Map.of("dirt", 1),
                        Map.of(),
                        now[0],
                        new GraphBlock("world:plan_breach:gone", "", new GraphBlock.Item("minecraft:dirt", 1)))),
                List.of(),
                List.of());

        board.restore(stale);

        assertEquals(
                List.of(),
                board.snapshot().blocked(),
                "Блокировка без билета не переживает восстановление: её некому снимать, а граф получал бы "
                        + "вечную ложную нужду"
                        + " Иначе тест restore_BlockedWithoutTicket_IsPruned перестанет ловить эту поломку. При смене restore_BlockedWithoutTicket_IsPruned проверь это утверждение.");
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
                "Старая блокировка без Need не висит вечно: условие пересчитывается заново, а не ждёт пустоту"
                        + " Иначе тест restore_LegacyBlockedWithoutNeed_ReturnsToPending перестанет ловить эту поломку. При смене restore_LegacyBlockedWithoutNeed_ReturnsToPending проверь это утверждение.");
    }
}
