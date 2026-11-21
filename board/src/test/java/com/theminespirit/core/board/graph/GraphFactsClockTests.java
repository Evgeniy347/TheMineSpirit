package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GraphFactsClockTests {
    @Test
    void of_DayPhases_NameDuskNightMorning() {
        assertTrue(
                GraphFeedback.Clock.of(0).morning(),
                "Начало суток — утро"
                        + " Иначе тест of_DayPhases_NameDuskNightMorning перестанет ловить эту поломку. При смене of_DayPhases_NameDuskNightMorning проверь это утверждение.");
        assertFalse(
                GraphFeedback.Clock.of(0).night(),
                "Начало суток — не ночь"
                        + " Иначе тест of_DayPhases_NameDuskNightMorning перестанет ловить эту поломку. При смене of_DayPhases_NameDuskNightMorning проверь это утверждение.");

        assertTrue(
                GraphFeedback.Clock.of(12000).duskSoon(),
                "Перед закатом граф видит dusk_soon"
                        + " Иначе тест of_DayPhases_NameDuskNightMorning перестанет ловить эту поломку. При смене of_DayPhases_NameDuskNightMorning проверь это утверждение.");
        assertFalse(
                GraphFeedback.Clock.of(12000).night(),
                "Закат ещё не ночь"
                        + " Иначе тест of_DayPhases_NameDuskNightMorning перестанет ловить эту поломку. При смене of_DayPhases_NameDuskNightMorning проверь это утверждение.");

        assertTrue(
                GraphFeedback.Clock.of(15000).night(),
                "Середина ночи — night"
                        + " Иначе тест of_DayPhases_NameDuskNightMorning перестанет ловить эту поломку. При смене of_DayPhases_NameDuskNightMorning проверь это утверждение.");
        assertFalse(
                GraphFeedback.Clock.of(15000).duskSoon(),
                "Глубокая ночь не «скоро закат»"
                        + " Иначе тест of_DayPhases_NameDuskNightMorning перестанет ловить эту поломку. При смене of_DayPhases_NameDuskNightMorning проверь это утверждение.");

        assertTrue(
                GraphFeedback.Clock.of(23000).morning(),
                "Рассвет — снова утро"
                        + " Иначе тест of_DayPhases_NameDuskNightMorning перестанет ловить эту поломку. При смене of_DayPhases_NameDuskNightMorning проверь это утверждение.");
        assertTrue(
                GraphFeedback.Clock.of(23000).duskSoon(),
                "Перед рассветом дорога домой ещё важна"
                        + " Иначе тест of_DayPhases_NameDuskNightMorning перестанет ловить эту поломку. При смене of_DayPhases_NameDuskNightMorning проверь это утверждение.");
    }

    @Test
    void of_DayWraps_ByFullDay() {
        GraphFeedback.Clock clock = GraphFeedback.Clock.of(24000 + 12500);

        assertEquals(
                12500,
                clock.dayTime(),
                "Время суток считается внутри суток"
                        + " Иначе тест of_DayWraps_ByFullDay перестанет ловить эту поломку. При смене of_DayWraps_ByFullDay проверь это утверждение.");
        assertTrue(
                clock.duskSoon(),
                "Закат повторяется каждые сутки"
                        + " Иначе тест of_DayWraps_ByFullDay перестанет ловить эту поломку. При смене of_DayWraps_ByFullDay проверь это утверждение.");
    }

    @Test
    void unknown_NoSignals_IsSafeInput() {
        GraphFeedback.Clock clock = GraphFeedback.Clock.unknown();

        assertEquals(
                -1,
                clock.dayTime(),
                "Неизвестное время отмечено -1"
                        + " Иначе тест unknown_NoSignals_IsSafeInput перестанет ловить эту поломку. При смене unknown_NoSignals_IsSafeInput проверь это утверждение.");
        assertFalse(
                clock.morning() || clock.duskSoon() || clock.night(),
                "Неизвестное время не поднимает сигналов"
                        + " Иначе тест unknown_NoSignals_IsSafeInput перестанет ловить эту поломку. При смене unknown_NoSignals_IsSafeInput проверь это утверждение.");
    }
}
