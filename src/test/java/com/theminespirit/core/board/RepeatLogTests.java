package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RepeatLogTests {
    @Test
    void note_FirstEvent_IsReturnedWithTotals() {
        RepeatLog log = new RepeatLog(6000);

        RepeatLog.Summary summary = log.note("tick", 1030, 100).orElseThrow();

        assertEquals(
                1,
                summary.count(),
                "Первое событие окна печатается сразу: иначе о поломке узнают только "
                        + "через сводку и потеряют начало приступа");
        assertEquals(1030, summary.min(), "В сводке обязано быть самое короткое время события: по нему видно разброс");
        assertEquals(1030, summary.max(), "В сводке обязано быть самое долгое время события: по нему судят о худшем");
        assertEquals(1030, summary.total(), "Сумма нужна для сводки: без неё не сказать, сколько всего набежало");
    }

    @Test
    void note_RepeatsInsideWindow_AreSilent() {
        RepeatLog log = new RepeatLog(6000);
        log.note("tick", 1030, 100);

        assertEquals(
                Optional.empty(),
                log.note("tick", 1100, 200),
                "Те же события внутри окна сводки не печатаются по одному: повтор — не новость, пока состояние "
                        + "или причина не сменились");
    }

    @Test
    void note_AfterWindow_ReturnsSummaryAndStartsNewWindow() {
        RepeatLog log = new RepeatLog(6000);
        log.note("tick", 1030, 100);
        log.note("tick", 1157, 500);

        RepeatLog.Summary summary = log.note("tick", 1100, 100 + 6000).orElseThrow();

        assertEquals(
                3,
                summary.count(),
                "Сводка обязана называть число повторов: иначе непонятно, приступ это или " + "единичный случай");
        assertEquals(1030, summary.min(), "Сводка держит минимум за окно: по нему видно начало просадки");
        assertEquals(1157, summary.max(), "Сводка держит максимум за окно: по нему видно худший тик");
        assertEquals(3287, summary.total(), "Сводка держит сумму за окно: по ней оценивают общую потерю времени");
        assertEquals(
                Optional.empty(),
                log.note("tick", 1100, 200 + 6000),
                "Новое окно начинается с нуля: повтор сразу после сводки ждёт следующего срока");
    }

    @Test
    void note_SlowMeasureBelowLimit_NeverBreaksFirstEvent() {
        RepeatLog log = new RepeatLog(6000);
        log.note("idle", 1, 10);

        assertTrue(log.note("idle", 1, 20).isEmpty(), "Пока окно не вышло, повторы молчат");
    }

    @Test
    void clear_NextEvent_IsPrintedAgain() {
        RepeatLog log = new RepeatLog(6000);
        log.note("rescue", 1, 10);
        log.clear("rescue");

        assertEquals(
                1,
                log.note("rescue", 1, 20).orElseThrow().count(),
                "После смены состояния счёт начинается заново: новый случай обязан попасть в лог сразу");
    }

    @Test
    void retain_ForgottenKey_StartsNewBurst() {
        RepeatLog log = new RepeatLog(6000);
        log.note("a", 1, 10);
        log.note("b", 1, 10);
        log.retain(Set.of("a"));

        assertEquals(
                1,
                log.note("b", 1, 20).orElseThrow().count(),
                "Канал, ушедший из наблюдения, не должен копить старое состояние: следующий случай снова первый");
    }
}
