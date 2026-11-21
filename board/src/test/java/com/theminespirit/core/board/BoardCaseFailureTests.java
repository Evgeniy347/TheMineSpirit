package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class BoardCaseFailureTests {
    private static final UUID SPIRIT_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SPIRIT_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final BoardCase CASE = BoardCase.STOCK_CHESTS;

    private static BoardTicket ticket(String id) {
        return new BoardTicket(
                id,
                CASE,
                Optional.empty(),
                BoardRules.priorityOf(CASE),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
    }

    private static void failOnce(Board board, String id, UUID spirit, long now) {
        board.submit(ticket(id));
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim(id, spirit, now),
                "Задание " + id
                        + " выдано духу: иначе провал не к чему привязать При смене механику проверь это утверждение. Иначе поломка пройдёт незамеченной в прогоне.");
        assertTrue(
                board.fail(id),
                "Дух провалил задание  Иначе тест механику перестанет ловить эту поломку. При смене механику проверь это утверждение. Иначе поломка пройдёт незамеченной в прогоне."
                        + id);
    }

    @Test
    void fail_SameSpiritSameCase_PausedUntilPauseEnds() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        failOnce(board, "first:0", SPIRIT_ONE, 0);
        long pause = BoardTuning.caseFailPauseTicks();

        now[0] = pause - 1;
        board.submit(ticket("first:1"));

        assertEquals(
                BoardOutcome.SPIRIT_PAUSED,
                board.claim("first:1", SPIRIT_ONE, now[0]),
                "Дух не берёт проваленный кейс за тик до конца паузы: иначе first:stock_chests "
                        + "возвращается к нему каждую пересборку и он снова стоит на месте При смене fail_SameSpiritSameCase_PausedUntilPauseEnds проверь это утверждение.");
        now[0] = pause;
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim("first:1", SPIRIT_ONE, now[0]),
                "Ровно через паузу кейс снова доступен этому духу: пауза не снимает его навсегда Иначе тест fail_SameSpiritSameCase_PausedUntilPauseEnds перестанет ловить эту поломку. При смене fail_SameSpiritSameCase_PausedUntilPauseEnds проверь это утверждение.");
    }

    @Test
    void fail_SecondFailure_DoublesSameSpiritPause() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        long first = BoardTuning.caseFailPauseTicks();
        failOnce(board, "first:0", SPIRIT_ONE, 0);
        assertEquals(
                first,
                board.casePauseTicks(SPIRIT_ONE, CASE),
                "Первый провал даёт начальную паузу: число в тесте — данные board.json Иначе тест fail_SecondFailure_DoublesSameSpiritPause перестанет ловить эту поломку. При смене fail_SecondFailure_DoublesSameSpiritPause проверь это утверждение.");

        now[0] = first;
        failOnce(board, "first:1", SPIRIT_ONE, now[0]);
        assertEquals(
                first * BoardTuning.caseFailMultiplier(),
                board.casePauseTicks(SPIRIT_ONE, CASE),
                "Второй провал подряд удлиняет паузу этим же духом: иначе он снова долбит тот же кейс При смене fail_SecondFailure_DoublesSameSpiritPause проверь это утверждение.");
        assertEquals(
                2,
                board.consecutiveFailures(SPIRIT_ONE, CASE),
                "Счётчик серии в тесте — 2 провала подряд у одного духа Иначе тест fail_SecondFailure_DoublesSameSpiritPause перестанет ловить эту поломку. При смене fail_SecondFailure_DoublesSameSpiritPause проверь это утверждение.");
    }

    @Test
    void complete_AfterFailure_ResetsPauseToInitial() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        long first = BoardTuning.caseFailPauseTicks();
        failOnce(board, "first:0", SPIRIT_ONE, 0);
        now[0] = first;
        failOnce(board, "first:1", SPIRIT_ONE, now[0]);
        now[0] = first + first * BoardTuning.caseFailMultiplier();
        board.submit(ticket("first:2"));
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim("first:2", SPIRIT_ONE, now[0]),
                "После паузы второй ступени дух снова берёт кейс Иначе тест complete_AfterFailure_ResetsPauseToInitial перестанет ловить эту поломку. При смене complete_AfterFailure_ResetsPauseToInitial проверь это утверждение.");
        assertTrue(
                board.complete("first:2"),
                "Успех закрывает задание и сбрасывает серию провалов Иначе тест complete_AfterFailure_ResetsPauseToInitial перестанет ловить эту поломку. При смене complete_AfterFailure_ResetsPauseToInitial проверь это утверждение.");

        failOnce(board, "first:3", SPIRIT_ONE, now[0]);
        assertEquals(
                first,
                board.casePauseTicks(SPIRIT_ONE, CASE),
                "Успех сбрасывает паузу до начальной: иначе редкие провалы копили бы экспоненту При смене complete_AfterFailure_ResetsPauseToInitial проверь это утверждение.");
    }

    @Test
    void fail_OtherSpiritSameCase_NotPaused() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        failOnce(board, "first:0", SPIRIT_ONE, 0);
        board.submit(ticket("first:1"));

        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim("first:1", SPIRIT_TWO, now[0]),
                "Провал одного духа не блокирует кейс для другого: иначе одно невезучее "
                        + "поселение не поставит сундуки никогда При смене fail_OtherSpiritSameCase_NotPaused проверь это утверждение.");
    }

    @Test
    void fail_RepeatedLimit_DetachesCaseAndWritesJournal() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        int limit = BoardTuning.caseFailLimit();
        for (int attempt = 1; attempt <= limit; attempt++) {
            failOnce(board, "first:" + attempt, SPIRIT_ONE, now[0]);
            now[0] += board.casePauseTicks(SPIRIT_ONE, CASE);
        }

        assertTrue(
                board.caseDetached(SPIRIT_ONE, CASE),
                "После " + limit
                        + " провалов подряд кейс снят с духа: иначе он берёт его снова и снова При смене fail_RepeatedLimit_DetachesCaseAndWritesJournal проверь это утверждение.");
        assertEquals(
                limit,
                board.consecutiveFailures(SPIRIT_ONE, CASE),
                "Счётчик провалов подряд доходит до предела: число в тесте — из production Иначе тест fail_RepeatedLimit_DetachesCaseAndWritesJournal перестанет ловить эту поломку. При смене fail_RepeatedLimit_DetachesCaseAndWritesJournal проверь это утверждение.");

        List<BoardLogEntry> journal = board.failures().drainDetachments();
        assertEquals(
                1,
                journal.size(),
                "Снятие кейса пишется в журнал ровно один раз: повторные записи прячут момент снятия Иначе тест fail_RepeatedLimit_DetachesCaseAndWritesJournal перестанет ловить эту поломку. При смене fail_RepeatedLimit_DetachesCaseAndWritesJournal проверь это утверждение.");
        assertEquals(
                BoardLog.Event.DETACHED,
                journal.get(0).event(),
                "Событие журнала — DETACHED: по нему разбирают, что кейс сняли с духа, а не провалили снова Иначе тест fail_RepeatedLimit_DetachesCaseAndWritesJournal перестанет ловить эту поломку. При смене fail_RepeatedLimit_DetachesCaseAndWritesJournal проверь это утверждение.");
        assertEquals(
                SPIRIT_ONE.toString(),
                journal.get(0).spirit(),
                "Журнал называет духа, с которого снят кейс: без него разбор бесконечного цикла невозможен Иначе тест fail_RepeatedLimit_DetachesCaseAndWritesJournal перестанет ловить эту поломку. При смене fail_RepeatedLimit_DetachesCaseAndWritesJournal проверь это утверждение.");
        assertTrue(
                board.failures().drainDetachments().isEmpty(),
                "Журнал отдаётся один раз: адаптер не пишет дубли Иначе тест fail_RepeatedLimit_DetachesCaseAndWritesJournal перестанет ловить эту поломку. При смене fail_RepeatedLimit_DetachesCaseAndWritesJournal проверь это утверждение.");

        board.submit(ticket("first:after"));
        assertEquals(
                BoardOutcome.SPIRIT_PAUSED,
                board.claim("first:after", SPIRIT_ONE, now[0]),
                "Снятый кейс не выдаётся этому духу до таймаута снятия: иначе он снова долбит тот же провал При смене fail_RepeatedLimit_DetachesCaseAndWritesJournal проверь это утверждение.");
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim("first:after", SPIRIT_TWO, now[0]),
                "Снятие с одного духа не снимает кейс с доски: его берёт другой исполнитель Иначе тест fail_RepeatedLimit_DetachesCaseAndWritesJournal перестанет ловить эту поломку. При смене fail_RepeatedLimit_DetachesCaseAndWritesJournal проверь это утверждение.");

        long release = BoardTuning.caseFailPauseCapTicks();
        for (int attempt = 1; attempt < limit; attempt++) {
            release += BoardBackoff.pauseTicks(attempt);
        }
        now[0] = release - 1;
        board.submit(ticket("first:late"));
        assertEquals(
                BoardOutcome.SPIRIT_PAUSED,
                board.claim("first:late", SPIRIT_ONE, now[0]),
                "За тик до таймаута снятый кейс ещё не возвращается духу Иначе тест fail_RepeatedLimit_DetachesCaseAndWritesJournal перестанет ловить эту поломку. При смене fail_RepeatedLimit_DetachesCaseAndWritesJournal проверь это утверждение.");
        now[0] = release;
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim("first:late", SPIRIT_ONE, now[0]),
                "По истечении таймаута снятие кончается и дух снова берёт кейс: иначе одно снятие "
                        + "навсегда паркует духа (живой прогон: idle 40 минут) При смене fail_RepeatedLimit_DetachesCaseAndWritesJournal проверь это утверждение.");
    }

    @Test
    void fail_EachAttemptBeforeLimit_PauseGrowsUnderCapAndEndsExactly() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        long previousPause = 0;
        for (int attempt = 1; attempt < BoardTuning.caseFailLimit(); attempt++) {
            failOnce(board, "fail:" + attempt, SPIRIT_ONE, now[0]);
            long pause = board.casePauseTicks(SPIRIT_ONE, CASE);
            assertTrue(
                    pause > 0,
                    "После провала у пары дух+кейс есть пауза, фактически  Иначе тест fail_EachAttemptBeforeLimit_PauseGrowsUnderCapAndEndsExactly перестанет ловить эту поломку. При смене fail_EachAttemptBeforeLimit_PauseGrowsUnderCapAndEndsExactly проверь это утверждение."
                            + pause);
            assertTrue(
                    attempt == 1 || pause > previousPause,
                    "Ступень " + attempt + " длиннее прошлой (" + pause + " > " + previousPause
                            + "): иначе откат не растёт и дух долбит задание с той же частотой При смене fail_EachAttemptBeforeLimit_PauseGrowsUnderCapAndEndsExactly проверь это утверждение.");
            assertTrue(
                    pause <= BoardTuning.caseFailPauseCapTicks(),
                    "Пауза " + pause
                            + " не выше потолка  Иначе тест fail_EachAttemptBeforeLimit_PauseGrowsUnderCapAndEndsExactly перестанет ловить эту поломку. При смене fail_EachAttemptBeforeLimit_PauseGrowsUnderCapAndEndsExactly проверь это утверждение."
                            + BoardTuning.caseFailPauseCapTicks());
            previousPause = pause;

            String next = "next:" + attempt;
            board.submit(ticket(next));
            now[0] += pause - 1;
            assertEquals(
                    BoardOutcome.SPIRIT_PAUSED,
                    board.claim(next, SPIRIT_ONE, now[0]),
                    "Попытка " + attempt + ": за тик до конца паузы (" + pause + ") тот же дух не получает тот же "
                            + "кейс: иначе цикл задания повторяется каждой пересборкой При смене fail_EachAttemptBeforeLimit_PauseGrowsUnderCapAndEndsExactly проверь это утверждение.");
            now[0] += 1;
            assertEquals(
                    BoardOutcome.CLAIMED,
                    board.claim(next, SPIRIT_ONE, now[0]),
                    "Попытка " + attempt
                            + ": ровно через паузу кейс снова доступен этому духу Иначе тест fail_EachAttemptBeforeLimit_PauseGrowsUnderCapAndEndsExactly перестанет ловить эту поломку. При смене fail_EachAttemptBeforeLimit_PauseGrowsUnderCapAndEndsExactly проверь это утверждение.");
            board.cancel(next);
        }
    }

    @Test
    void complete_AfterFailure_ResetsStreak() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        failOnce(board, "first:0", SPIRIT_ONE, 0);
        now[0] = BoardTuning.caseFailPauseTicks();
        board.submit(ticket("first:1"));
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim("first:1", SPIRIT_ONE, now[0]),
                "После паузы дух снова берёт кейс Иначе тест complete_AfterFailure_ResetsStreak перестанет ловить эту поломку. При смене complete_AfterFailure_ResetsStreak проверь это утверждение.");
        assertTrue(
                board.complete("first:1"),
                "Успех закрывает задание Иначе тест complete_AfterFailure_ResetsStreak перестанет ловить эту поломку. При смене complete_AfterFailure_ResetsStreak проверь это утверждение.");

        assertEquals(
                0,
                board.consecutiveFailures(SPIRIT_ONE, CASE),
                "Успех сбрасывает серию провалов: иначе редкие провалы копились бы до снятия кейса При смене complete_AfterFailure_ResetsStreak проверь это утверждение.");
        failOnce(board, "first:2", SPIRIT_ONE, now[0]);
        assertFalse(
                board.caseDetached(SPIRIT_ONE, CASE),
                "После сброса один провал не снимает кейс: предел считается по подряд идущим провалам Иначе тест complete_AfterFailure_ResetsStreak перестанет ловить эту поломку. При смене complete_AfterFailure_ResetsStreak проверь это утверждение.");
    }

    @Property(tries = 100)
    void failure_AnyWaitWithinPause_DoesNotLetSameSpiritTakeCase(@ForAll("waitWithinPause") long wait) {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        failOnce(board, "first:0", SPIRIT_ONE, 0);

        now[0] = wait;
        board.submit(ticket("first:1"));

        assertEquals(
                BoardOutcome.SPIRIT_PAUSED,
                board.claim("first:1", SPIRIT_ONE, now[0]),
                "При любом ожидании " + wait + " из паузы дух не получает тот же кейс: "
                        + "иначе провалы крутят его чаще, чем раз в " + BoardTuning.caseFailPauseTicks()
                        + " тиков При смене failure_AnyWaitWithinPause_DoesNotLetSameSpiritTakeCase проверь это утверждение.");
    }

    @Provide
    Arbitrary<Long> waitWithinPause() {
        return Arbitraries.longs().between(0, BoardTuning.caseFailPauseTicks() - 1);
    }
}
