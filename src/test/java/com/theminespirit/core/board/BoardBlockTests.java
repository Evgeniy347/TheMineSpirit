package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardBlockTests {
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
    void fail_LimitReached_BlocksTicketAndWritesReason() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        int limit = BoardTuning.ticketFailLimit();
        for (int attempt = 1; attempt <= limit; attempt++) {
            if (attempt > 1) {
                board.prune(now[0]);
                now[0] += BoardTuning.failBackoffTicks();
            }
            assertEquals(
                    BoardOutcome.ACCEPTED,
                    board.submit(ticket()),
                    "Задание " + ID + " ставится заново после провала: попытка " + attempt);
            assertEquals(
                    BoardOutcome.CLAIMED,
                    board.claim(ID, spirit(attempt), now[0]),
                    "Задание выдано духу " + attempt + ": провал принадлежит конкретному исполнителю");
            assertTrue(board.fail(ID), "Провал " + attempt + " записан");
            if (attempt < limit) {
                assertFalse(board.blocked(ID), "До предела " + limit + " задание ещё не заблокировано");
            }
        }

        assertTrue(
                board.blocked(ID),
                "После " + limit + " провалов подряд задание заблокировано: "
                        + "иначе доска переиздаёт его бесконечно");
        assertEquals(
                Optional.of(BoardStatus.BLOCKED),
                board.statusOf(ID),
                "Статус задания — BLOCKED, а не PENDING: наблюдатель видит причину остановки");
        assertEquals(
                BoardOutcome.BLOCKED,
                board.claim(ID, spirit(limit + 1), now[0]),
                "Заблокированное задание не выдаётся даже другому духу: условие не изменилось");

        List<BoardLogEntry> journal = board.drainBlocks();
        assertEquals(1, journal.size(), "Блокировка пишется в журнал ровно один раз");
        assertEquals(BoardLog.Event.BLOCKED, journal.get(0).event(), "Событие журнала — BLOCKED");
        assertTrue(
                journal.get(0).modifiers().contains("failures=" + limit),
                "Причина в журнале называет число провалов: " + journal.get(0).modifiers());
        assertTrue(
                journal.get(0).modifiers().contains("missing=dirt:9"),
                "Причина в журнале называет нехватку: " + journal.get(0).modifiers());
        assertTrue(board.drainBlocks().isEmpty(), "Запись отдаётся один раз: адаптер не пишет дубли");
    }

    @Test
    void fail_SameStuckPointTwice_BlockedWithoutBurningLimit() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        String stuck = "застрял в (-175.3, 67.0, -487.5)";
        for (int attempt = 1; attempt <= 2; attempt++) {
            if (attempt > 1) {
                board.prune(now[0]);
                now[0] += BoardTuning.failBackoffTicks();
            }
            board.submit(ticket());
            board.claim(ID, spirit(attempt), now[0]);
            board.fail(ID, new StockView(Map.of()), stuck);
        }

        assertTrue(
                board.blocked(ID),
                "Тот же «застрял» в той же точке у второго духа — дело места, а не духа: задание блокируется сразу, "
                        + "а не сжигает " + BoardTuning.ticketFailLimit() + " попыток (INC-63)");
    }

    @Test
    void fail_DifferentReasons_CountedToLimit() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        board.submit(ticket());
        board.claim(ID, spirit(1), now[0]);
        board.fail(ID, new StockView(Map.of()), "застрял в (1, 2, 3)");
        board.prune(now[0]);
        now[0] += BoardTuning.failBackoffTicks();
        board.submit(ticket());
        board.claim(ID, spirit(2), now[0]);
        board.fail(ID, new StockView(Map.of()), "нет материала");

        assertFalse(board.blocked(ID), "Разные причины провала — обычный счёт до предела попыток");
    }

    @Test
    void fail_Blocked_KeepsReasonAndAttemptHistoryInJournal() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        String stuck = "застрял в (-175.3, 67.0, -487.5)";
        for (int attempt = 1; attempt <= 2; attempt++) {
            if (attempt > 1) {
                board.prune(now[0]);
                now[0] += BoardTuning.failBackoffTicks();
            }
            board.submit(ticket());
            board.claim(ID, spirit(attempt), now[0]);
            board.fail(ID, new StockView(Map.of()), stuck);
        }

        BoardLogEntry blocked = board.drainBlocks().get(0);

        assertTrue(
                blocked.modifiers().stream().anyMatch(modifier -> modifier.contains(stuck)),
                "BLOCKED называет причину последнего провала: пустой waitReason не показывает, что чинить "
                        + "(п.4 отчёта); модификаторы: " + blocked.modifiers());
        assertTrue(
                blocked.modifiers().stream().anyMatch(modifier -> modifier.startsWith("history=")),
                "BLOCKED хранит историю попыток, а не только счётчик: по ней видно, один и тот же тупик "
                        + "или разные причины; модификаторы: " + blocked.modifiers());
        assertTrue(
                blocked.modifiers().stream().anyMatch(modifier -> modifier.startsWith("failures=")),
                "BLOCKED по-прежнему называет число провалов: история дополняет счётчик, а не заменяет его; "
                        + "модификаторы: " + blocked.modifiers());
    }

    @Test
    void prune_BlockedLongEnough_StaysBlockedWithoutConditionChange() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        for (int attempt = 1; attempt <= BoardTuning.ticketFailLimit(); attempt++) {
            UUID holder = spirit(attempt);
            board.submit(ticket());
            board.claim(ID, holder, now[0]);
            board.fail(ID);
            board.prune(now[0]);
            now[0] += Math.max(BoardTuning.failBackoffTicks(), board.casePauseTicks(holder, CASE));
        }
        assertTrue(board.blocked(ID), "Сцена: задание заблокировано провалами подряд");
        board.prune(now[0]);
        now[0] += 100_000;

        board.prune(now[0]);

        assertEquals(
                BoardStatus.BLOCKED,
                board.statusOf(ID).orElse(null),
                "BLOCKED не снимается по времени: без изменения условий доска снова выдаст тот же тупик "
                        + "(N6: BLOCKED→UNBLOCKED без изменения условий)");
        assertEquals(
                BoardOutcome.BLOCKED,
                board.claim(ID, spirit(99), now[0]),
                "Заблокированное задание не выдаётся и после паузы: разблокировка только при смене условий");
    }

    @Test
    void claimable_Unblocked_NoStaleHolder() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        int limit = BoardTuning.ticketFailLimit();
        for (int attempt = 1; attempt <= limit; attempt++) {
            if (attempt > 1) {
                board.prune(now[0]);
                now[0] += BoardTuning.failBackoffTicks();
            }
            board.submit(ticket());
            board.claim(ID, spirit(attempt), now[0]);
            board.fail(ID);
        }

        board.claimable(new StockView(Map.of("dirt", 9)), now[0]);

        assertEquals(
                Optional.empty(),
                board.holderOf(ID),
                "Снова PENDING — без держателя: «PENDING с assignee» висел на доске, будто задание взято (INC-47)");
    }

    @Test
    void claimable_MaterialsAppeared_UnblocksAndWritesJournal() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        int limit = BoardTuning.ticketFailLimit();
        for (int attempt = 1; attempt <= limit; attempt++) {
            if (attempt > 1) {
                board.prune(now[0]);
                now[0] += BoardTuning.failBackoffTicks();
            }
            board.submit(ticket());
            board.claim(ID, spirit(attempt), now[0]);
            board.fail(ID);
        }
        assertTrue(board.blocked(ID), "Подготовка: задание заблокировано пределом провалов");
        board.drainBlocks();

        List<BoardTicket> ready = board.claimable(new StockView(Map.of("dirt", 9)), now[0]);
        assertTrue(
                ready.stream().anyMatch(ticket -> ID.equals(ticket.id())),
                "Появившийся материал снимает блокировку: задание снова готово к выдаче");
        assertEquals(
                Optional.of(BoardStatus.PENDING),
                board.statusOf(ID),
                "Разблокированное задание ждёт исполнителя, а не остаётся BLOCKED");
        assertEquals(0, board.ticketFailures(ID), "Разблокировка начинает серию провалов заново");

        List<BoardLogEntry> journal = board.drainBlocks();
        assertEquals(1, journal.size(), "Снятие блокировки пишется в журнал ровно один раз");
        assertEquals(BoardLog.Event.UNBLOCKED, journal.get(0).event(), "Событие журнала — UNBLOCKED");
        assertTrue(
                journal.get(0).modifiers().contains("failures=0"),
                "Снятие блокировки называет сброшенный счётчик: "
                        + journal.get(0).modifiers());
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim(ID, spirit(limit + 1), now[0]),
                "После разблокировки задание снова выдаётся свободному духу");
    }
}
