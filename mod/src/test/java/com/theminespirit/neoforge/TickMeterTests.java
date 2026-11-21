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

        assertTrue(
                meter.observe(40_000_000L).isEmpty(),
                "До конца минуты сводка не пишется: иначе лог тонет"
                        + " При смене observe_FirstWindowNotFull_NoLineYet проверь это утверждение.");
    }

    @Test
    void observe_FullWindow_ReportsAverageAndWorstAndResets() {
        TickMeter meter = new TickMeter(2);

        meter.observe(40_000_000L);
        Optional<String> line = meter.observe(60_000_000L);

        assertTrue(
                line.isPresent(),
                "Полное окно обязано давать строку [tick]"
                        + " Иначе тест observe_FullWindow_ReportsAverageAndWorstAndResets перестанет ловить эту поломку. При смене observe_FullWindow_ReportsAverageAndWorstAndResets проверь это утверждение.");
        assertTrue(
                line.orElseThrow().contains("MSPT 50.0"),
                "MSPT — среднее по окну: " + line
                        + " Иначе тест observe_FullWindow_ReportsAverageAndWorstAndResets перестанет ловить эту поломку. При смене observe_FullWindow_ReportsAverageAndWorstAndResets проверь это утверждение.");
        assertTrue(
                line.orElseThrow().contains("худший 60.0"),
                "Худший тик нужен для поиска просадки: " + line
                        + " Иначе тест observe_FullWindow_ReportsAverageAndWorstAndResets перестанет ловить эту поломку. При смене observe_FullWindow_ReportsAverageAndWorstAndResets проверь это утверждение.");
        assertTrue(
                meter.observe(50_000_000L).isEmpty(),
                "После сводки окно начинается заново"
                        + " Иначе тест observe_FullWindow_ReportsAverageAndWorstAndResets перестанет ловить эту поломку. При смене observe_FullWindow_ReportsAverageAndWorstAndResets проверь это утверждение.");
    }

    @Test
    void line_TickCountAndWorst_TellWindowAndSpike() {
        String line = TickMeter.line(1200, 1200L * 20_000_000L, 90_000_000L);

        assertEquals(
                "[tick] MSPT 20.0, худший 90.0 за 1200 тиков",
                line,
                "Формат [tick] называет среднее, худшее за окно: по нему видно просадку без «Can't keep up»"
                        + " Иначе тест line_TickCountAndWorst_TellWindowAndSpike перестанет ловить эту поломку. При смене line_TickCountAndWorst_TellWindowAndSpike проверь это утверждение.");
        assertFalse(
                line.contains("null"),
                "В строке не должно быть пустых полей: " + line
                        + " Иначе тест line_TickCountAndWorst_TellWindowAndSpike перестанет ловить эту поломку. При смене line_TickCountAndWorst_TellWindowAndSpike проверь это утверждение.");
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

        assertTrue(
                failed,
                "Отрицательная длительность тика — ошибка времени, а не тихий ноль"
                        + " Иначе тест observe_NegativeElapsed_IsRejected перестанет ловить эту поломку. При смене observe_NegativeElapsed_IsRejected проверь это утверждение.");
    }
}
