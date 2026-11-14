package com.theminespirit.core.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PerfTests {
    @Test
    void report_WindowOfSpans_SummedSortedAndDrained() {
        Perf.time("spirit.tactics", () -> {});
        Perf.time("spirit.hunger", () -> {});
        assertThrows(
                IllegalStateException.class,
                () -> Perf.time("spirit.broken", () -> {
                    throw new IllegalStateException("участок упал");
                }),
                "исключение участка проходит наружу: Perf.time не глотает ошибки вызывающего кода");
        Perf.record("board.build.world", 5_000_000);
        Perf.record("board.build.world", 1_000_000);

        List<Perf.Line> first = Perf.report();

        assertEquals("board.build.world", first.get(0).name(), "самый долгий по сумме участок — первый в сводке");
        assertEquals(
                List.of("spirit.broken", "spirit.hunger", "spirit.tactics"),
                first.stream().skip(1).map(Perf.Line::name).sorted().toList(),
                "Сводка обязана учитывать каждый вызов time/record, включая участок, упавший исключением: без "
                        + "него потерянный тик остаётся невидимым в разборе «почему сервер лагает». Пропавший "
                        + "участок ломает диагностику по [perf]-строке в логе (порядок пустых участков по "
                        + "наносекундам не проверяем — он неустойчив)");
        Perf.Line world = first.get(0);
        assertEquals(2, world.count(), "оба вызова record посчитаны: без счётчика вызовов среднее время врёт");
        assertEquals(6, world.totalNanos() / 1_000_000, "сумма окна — база для «топ по сумме»");
        assertEquals(5, world.maxNanos() / 1_000_000, "худший вызов хранится отдельно: пики видны и в частых участках");

        assertTrue(
                Perf.report().isEmpty(),
                "report забирает окно целиком: следующая сводка без новых замеров пуста. Остаток прошлых "
                        + "чисел смешивал бы окна и путал разбор «что изменилось после правки»");
    }
}
