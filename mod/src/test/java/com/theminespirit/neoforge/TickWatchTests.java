package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class TickWatchTests {
    private static final long WINDOW = 1_000_000_000L;

    @Test
    void describe_UnderLimit_IsSilent() {
        assertNull(
                TickWatch.describe("RouteDetour", 499_999_999L),
                "Операция короче 500 мс не попадает в лог: иначе сторож шумит на обычных тиках");
    }

    @Test
    void describe_OverLimit_NamesOperationAndMilliseconds() {
        String report = TickWatch.describe("RouteDetour", 2_750_000_000L);

        assertEquals(
                "[tick] операция RouteDetour держала серверный поток 2750 мс",
                report,
                "Сообщение сторожа называет класс операции и время: по нему ищут виновника в живом мире");
    }

    @Test
    void observe_FirstSlow_IsReportedImmediately() {
        TickWatch watch = new TickWatch(WINDOW);

        assertEquals(
                "[tick] операция BoardService держала серверный поток 1030 мс",
                watch.observe("BoardService", 1_030_000_000L, 10).orElseThrow(),
                "Первый медленный тик печатается сразу: иначе о подвисании узнают только из сводки и потеряют "
                        + "начало приступа");
    }

    @Test
    void observe_SameOperationWithinWindow_IsSilent() {
        TickWatch watch = new TickWatch(WINDOW);
        watch.observe("BoardService", 1_030_000_000L, 10);

        assertEquals(
                Optional.empty(),
                watch.observe("BoardService", 1_100_000_000L, 20),
                "То же подвисание внутри окна сводки не печатается по строке каждые 5 секунд: повтор — не новость");
    }

    @Test
    void observe_AfterWindow_ReturnsSummaryWithCountAndRange() {
        TickWatch watch = new TickWatch(WINDOW);
        watch.observe("BoardService", 1_030_000_000L, 10);
        watch.observe("BoardService", 1_157_000_000L, 20);

        String text = watch.observe("BoardService", 1_100_000_000L, 10 + WINDOW).orElseThrow();

        assertTrue(
                text.contains("3 раз"),
                "Сводка обязана называть число подвисаний: единичный случай и приступ " + "различают по числу");
        assertTrue(text.contains("1030–1157 мс"), "Сводка обязана называть разброс: максимум показывает худший тик");
        assertTrue(text.contains("3287 мс"), "Сводка обязана называть сумму: по ней оценивают потерю времени");
    }

    @Test
    void observe_FastTickAfterSlow_StartsNewReport() {
        TickWatch watch = new TickWatch(WINDOW);
        watch.observe("BoardService", 1_030_000_000L, 10);
        watch.observe("BoardService", 100_000_000L, 20);

        String text = watch.observe("BoardService", 1_500_000_000L, 30).orElseThrow();

        assertTrue(
                text.contains("1500 мс"),
                "Новое подвисание после нормального тика обязано снова печататься сразу: приступ закончился и "
                        + "начался заново");
    }
}
