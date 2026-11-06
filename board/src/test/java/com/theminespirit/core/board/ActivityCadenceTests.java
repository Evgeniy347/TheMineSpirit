package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ActivityCadenceTests {
    @Test
    void due_TenTicks_ReconcilesAgain() {
        assertTrue(
                ActivityCadence.due(100, 110),
                "Карточка занятия обновляется каждые 10 тиков: иначе короткое действие (еда, dig_out, команда) "
                        + "завершается между пересборками и не попадает на экран");
    }

    @Test
    void due_BeforeInterval_Waits() {
        assertFalse(
                ActivityCadence.due(100, 109),
                "До интервала карточку не трогаем: иначе доска пересобирается " + "на каждом тике и ест процессор");
    }

    @Test
    void due_TimeBackwards_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ActivityCadence.due(100, 99),
                "Время не идёт назад: отрицательная дельта обязана падать, а не молча держать старую карточку");
    }
}
