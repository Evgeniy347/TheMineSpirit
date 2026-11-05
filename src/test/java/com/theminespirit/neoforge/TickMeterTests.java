package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class TickMeterTests {
    @Test
    void observe_FirstWindowNotFull_NoLineYet() {
        TickMeter meter = new TickMeter(2);

        assertTrue(meter.observe(40_000_000L).isEmpty(), "До конца минуты сводка не пишется: иначе лог тонет");
    }

    @Test
    void observe_FullWindow_ReportsAverageAndWorstAndResets() {
        TickMeter meter = new TickMeter(2);

        meter.observe(40_000_000L);
        Optional<String> line = meter.observe(60_000_000L);

        assertTrue(line.isPresent(), "Полное окно обязано давать строку [tick]");
        assertTrue(line.orElseThrow().contains("MSPT 50.0"), "MSPT — среднее по окну: " + line);
        assertTrue(line.orElseThrow().contains("худший 60.0"), "Худший тик нужен для поиска просадки: " + line);
        assertTrue(meter.observe(50_000_000L).isEmpty(), "После сводки окно начинается заново");
    }

    @Test
    void line_TickCountAndWorst_TellWindowAndSpike() {
        String line = TickMeter.line(1200, 1200L * 20_000_000L, 90_000_000L);

        assertEquals(
                "[tick] MSPT 20.0, худший 90.0 за 1200 тиков",
                line,
                "Формат [tick] называет среднее, худшее за окно: по нему видно просадку без «Can't keep up»");
        assertFalse(line.contains("null"), "В строке не должно быть пустых полей: " + line);
    }

    @Test
    void observe_NegativeElapsed_IsRejected() {
        TickMeter meter = new TickMeter(1);

        boolean failed = false;
        try {
            meter.observe(-1L);
        } catch (IllegalArgumentException expected) {
            failed = true;
        }

        assertTrue(failed, "Отрицательная длительность тика — ошибка времени, а не тихий ноль");
    }
}
