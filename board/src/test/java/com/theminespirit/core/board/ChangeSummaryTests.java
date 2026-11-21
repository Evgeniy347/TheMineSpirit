package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChangeSummaryTests {
    @Test
    void note_FirstState_IsChangedAndPrinted() {
        ChangeSummary log = new ChangeSummary(6000);

        ChangeSummary.Summary summary =
                log.note("tick", "BoardService", 1030, 10).orElseThrow();

        assertTrue(
                summary.changed(),
                "Первое состояние печатается сразу: иначе начало приступа теряется за окном сводки"
                        + " При смене note_FirstState_IsChangedAndPrinted проверь это утверждение.");
        assertEquals(
                1030,
                summary.total(),
                "Первый случай обязан попасть в накопление: иначе сводка недосчитает"
                        + " При смене note_FirstState_IsChangedAndPrinted проверь это утверждение.");
    }

    @Test
    void note_SameStateInsideWindow_IsSilent() {
        ChangeSummary log = new ChangeSummary(6000);
        log.note("tick", "BoardService", 1030, 10);

        assertEquals(
                Optional.empty(),
                log.note("tick", "BoardService", 1100, 20),
                "Повтор того же состояния внутри окна не печатается: именно он заливал лог каждые 5 секунд"
                        + " Иначе тест note_SameStateInsideWindow_IsSilent перестанет ловить эту поломку. При смене note_SameStateInsideWindow_IsSilent проверь это утверждение.");
    }

    @Test
    void note_SameStateAfterWindow_ReturnsAccumulatedSummary() {
        ChangeSummary log = new ChangeSummary(6000);
        log.note("tick", "BoardService", 1030, 10);
        log.note("tick", "BoardService", 1157, 20);

        ChangeSummary.Summary summary =
                log.note("tick", "BoardService", 1100, 6000 + 10).orElseThrow();

        assertTrue(
                !summary.changed(),
                "Сводка повторов помечена как повтор: по ней не открывают новый случай"
                        + " Иначе тест note_SameStateAfterWindow_ReturnsAccumulatedSummary перестанет ловить эту поломку. При смене note_SameStateAfterWindow_ReturnsAccumulatedSummary проверь это утверждение.");
        assertEquals(
                3,
                summary.count(),
                "Сводка обязана называть число повторов за окно: иначе не видно приступа"
                        + " При смене note_SameStateAfterWindow_ReturnsAccumulatedSummary проверь это утверждение.");
        assertEquals(
                1030,
                summary.min(),
                "Сводка держит минимум: по нему видно начало просадки"
                        + " Иначе тест note_SameStateAfterWindow_ReturnsAccumulatedSummary перестанет ловить эту поломку. При смене note_SameStateAfterWindow_ReturnsAccumulatedSummary проверь это утверждение.");
        assertEquals(
                1157,
                summary.max(),
                "Сводка держит максимум: по нему видно худший случай"
                        + " Иначе тест note_SameStateAfterWindow_ReturnsAccumulatedSummary перестанет ловить эту поломку. При смене note_SameStateAfterWindow_ReturnsAccumulatedSummary проверь это утверждение.");
        assertEquals(
                3287,
                summary.total(),
                "Сводка держит сумму: по ней оценивают общую потерю"
                        + " Иначе тест note_SameStateAfterWindow_ReturnsAccumulatedSummary перестанет ловить эту поломку. При смене note_SameStateAfterWindow_ReturnsAccumulatedSummary проверь это утверждение.");
    }

    @Test
    void note_StateChanged_PrintsAgainImmediately() {
        ChangeSummary log = new ChangeSummary(6000);
        log.note("tick", "BoardService", 1030, 10);

        ChangeSummary.Summary summary = log.note("tick", "SpiritBrain", 900, 20).orElseThrow();

        assertTrue(
                summary.changed(),
                "Смена состояния печатается сразу: повтор — только пока состояние то же"
                        + " Иначе тест note_StateChanged_PrintsAgainImmediately перестанет ловить эту поломку. При смене note_StateChanged_PrintsAgainImmediately проверь это утверждение.");
        assertEquals(
                900,
                summary.total(),
                "Смена состояния начинает новое накопление, а не смешивает старое"
                        + " Иначе тест note_StateChanged_PrintsAgainImmediately перестанет ловить эту поломку. При смене note_StateChanged_PrintsAgainImmediately проверь это утверждение.");
    }

    @Test
    void clear_StateAfterEpisode_StartsChangedAgain() {
        ChangeSummary log = new ChangeSummary(6000);
        log.note("air", "surface|full", 300, 10);
        log.clear("air");

        assertTrue(
                log.note("air", "surface|full", 300, 20).orElseThrow().changed(),
                "Новый приступ после выхода из воды снова первый: старое состояние эпизода не глушит его строку"
                        + " Иначе тест clear_StateAfterEpisode_StartsChangedAgain перестанет ловить эту поломку. При смене clear_StateAfterEpisode_StartsChangedAgain проверь это утверждение.");
    }

    @Test
    void retain_ForgottenKey_StartsChangedAgain() {
        ChangeSummary log = new ChangeSummary(6000);
        log.note("rescue", "сам не выйдет", 1, 10);
        log.retain(Set.of("other"));

        assertTrue(
                log.note("rescue", "сам не выйдет", 1, 20).orElseThrow().changed(),
                "Канал, ушедший из наблюдения, не копит старое состояние: новый случай снова первый"
                        + " Иначе тест retain_ForgottenKey_StartsChangedAgain перестанет ловить эту поломку. При смене retain_ForgottenKey_StartsChangedAgain проверь это утверждение.");
    }
}
