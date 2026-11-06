package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GraphFactsClockTests {
    @Test
    void of_DayPhases_NameDuskNightMorning() {
        assertTrue(GraphFeedback.Clock.of(0).morning(), "Начало суток — утро");
        assertFalse(GraphFeedback.Clock.of(0).night(), "Начало суток — не ночь");

        assertTrue(GraphFeedback.Clock.of(12000).duskSoon(), "Перед закатом граф видит dusk_soon");
        assertFalse(GraphFeedback.Clock.of(12000).night(), "Закат ещё не ночь");

        assertTrue(GraphFeedback.Clock.of(15000).night(), "Середина ночи — night");
        assertFalse(GraphFeedback.Clock.of(15000).duskSoon(), "Глубокая ночь не «скоро закат»");

        assertTrue(GraphFeedback.Clock.of(23000).morning(), "Рассвет — снова утро");
        assertTrue(GraphFeedback.Clock.of(23000).duskSoon(), "Перед рассветом дорога домой ещё важна");
    }

    @Test
    void of_DayWraps_ByFullDay() {
        GraphFeedback.Clock clock = GraphFeedback.Clock.of(24000 + 12500);

        assertEquals(12500, clock.dayTime(), "Время суток считается внутри суток");
        assertTrue(clock.duskSoon(), "Закат повторяется каждые сутки");
    }

    @Test
    void unknown_NoSignals_IsSafeInput() {
        GraphFeedback.Clock clock = GraphFeedback.Clock.unknown();

        assertEquals(-1, clock.dayTime(), "Неизвестное время отмечено -1");
        assertFalse(clock.morning() || clock.duskSoon() || clock.night(), "Неизвестное время не поднимает сигналов");
    }
}
