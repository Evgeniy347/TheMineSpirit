package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Test;

class BoardPauseBackoffTests {

    @Test
    void pause_FirstFailure_EqualsLiveInitialPause() {
        assertEquals(
                BoardTuning.caseFailPauseTicks(),
                BoardBackoff.pauseTicks(1),
                "Первый провал даёт начальную паузу из board.json: иначе дух возвращается к сбойному "
                        + "кейсу раньше, чем успевает смениться обстановка"
                        + " При смене pause_FirstFailure_EqualsLiveInitialPause проверь это утверждение.");
    }

    @Test
    void pause_EachFailure_DoublesUntilEightMinuteCap() {
        assertEquals(
                1200,
                BoardBackoff.pauseTicks(1),
                "Ступень 1 — начальная пауза 1200 тиков"
                        + " Иначе тест pause_EachFailure_DoublesUntilEightMinuteCap перестанет ловить эту поломку. При смене pause_EachFailure_DoublesUntilEightMinuteCap проверь это утверждение.");
        assertEquals(
                2400,
                BoardBackoff.pauseTicks(2),
                "Ступень 2 — удвоенная пауза 2400 тиков"
                        + " Иначе тест pause_EachFailure_DoublesUntilEightMinuteCap перестанет ловить эту поломку. При смене pause_EachFailure_DoublesUntilEightMinuteCap проверь это утверждение.");
        assertEquals(
                4800,
                BoardBackoff.pauseTicks(3),
                "Ступень 3 — 4800 тиков"
                        + " Иначе тест pause_EachFailure_DoublesUntilEightMinuteCap перестанет ловить эту поломку. При смене pause_EachFailure_DoublesUntilEightMinuteCap проверь это утверждение.");
        assertEquals(
                9600,
                BoardBackoff.pauseTicks(4),
                "Ступень 4 — потолок 9600 тиков"
                        + " Иначе тест pause_EachFailure_DoublesUntilEightMinuteCap перестанет ловить эту поломку. При смене pause_EachFailure_DoublesUntilEightMinuteCap проверь это утверждение.");
        assertEquals(
                9600,
                BoardBackoff.pauseTicks(100),
                "Потолок держится на любой серии провалов: " + "экспонента не выключает духа навсегда"
                        + " Иначе тест pause_EachFailure_DoublesUntilEightMinuteCap перестанет ловить эту поломку. При смене pause_EachFailure_DoublesUntilEightMinuteCap проверь это утверждение.");
    }

    @Test
    void pause_BadArguments_Throw() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BoardBackoff.pauseTicks(0),
                "Пауза до первого провала не считается: нулевой серии нет"
                        + " Иначе тест pause_BadArguments_Throw перестанет ловить эту поломку. При смене pause_BadArguments_Throw проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> BoardBackoff.pauseTicks(1, 0, 2, 10),
                "Начальная пауза 0 тиков значит отсутствие отката — это ошибка данных"
                        + " Иначе тест pause_BadArguments_Throw перестанет ловить эту поломку. При смене pause_BadArguments_Throw проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> BoardBackoff.pauseTicks(1, 1, 0, 10),
                "Множитель 0 гасил бы паузу вместо роста — это ошибка данных"
                        + " Иначе тест pause_BadArguments_Throw перестанет ловить эту поломку. При смене pause_BadArguments_Throw проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> BoardBackoff.pauseTicks(1, 1, 2, 0),
                "Потолок 0 запрещал бы любую паузу — это ошибка данных"
                        + " Иначе тест pause_BadArguments_Throw перестанет ловить эту поломку. При смене pause_BadArguments_Throw проверь это утверждение.");
    }

    @Property(tries = 200)
    void pause_AnySeries_GrowsButNeverExceedsCap(
            @ForAll @LongRange(min = 1, max = 5_000) long initial,
            @ForAll @LongRange(min = 1, max = 10) long multiplier,
            @ForAll @LongRange(min = 1, max = 50_000) long cap,
            @ForAll @IntRange(min = 1, max = 60) int failures) {
        long previous = BoardBackoff.pauseTicks(1, initial, multiplier, cap);
        assertEquals(
                Math.min(initial, cap),
                previous,
                "Ступень 1 — начальная пауза, но не выше потолка: было " + previous
                        + " Иначе тест pause_AnySeries_GrowsButNeverExceedsCap перестанет ловить эту поломку. При смене pause_AnySeries_GrowsButNeverExceedsCap проверь это утверждение.");
        for (int step = 2; step <= failures; step++) {
            long pause = BoardBackoff.pauseTicks(step, initial, multiplier, cap);
            assertTrue(
                    pause >= previous,
                    "Пауза на шаге " + step + " (" + pause + ") не короче прошлой (" + previous + "): "
                            + "откат обязан расти или стоять на потолке"
                            + " Иначе тест pause_AnySeries_GrowsButNeverExceedsCap перестанет ловить эту поломку. При смене pause_AnySeries_GrowsButNeverExceedsCap проверь это утверждение.");
            if (previous < cap && multiplier > 1) {
                assertTrue(
                        pause > previous,
                        "Пока не достигнут потолок " + cap + ", множитель " + multiplier
                                + " обязан удлинять паузу: шаг " + step + " дал " + pause
                                + " вместо роста от " + previous
                                + " Иначе тест pause_AnySeries_GrowsButNeverExceedsCap перестанет ловить эту поломку. При смене pause_AnySeries_GrowsButNeverExceedsCap проверь это утверждение.");
            }
            assertTrue(
                    pause <= cap,
                    "Пауза на шаге " + step + " (" + pause + ") не выше потолка " + cap
                            + " Иначе тест pause_AnySeries_GrowsButNeverExceedsCap перестанет ловить эту поломку. При смене pause_AnySeries_GrowsButNeverExceedsCap проверь это утверждение.");
            previous = pause;
        }
        if (initial >= cap) {
            assertEquals(
                    cap,
                    BoardBackoff.pauseTicks(failures, initial, multiplier, cap),
                    "Начальная пауза выше потолка сразу упирается в потолок: иначе данные обходили бы предел"
                            + " При смене pause_AnySeries_GrowsButNeverExceedsCap проверь это утверждение.");
        }
    }
}
