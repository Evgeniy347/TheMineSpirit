package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SourceFailureLogTests {
    @Test
    void observe_FirstFailure_IsChanged() {
        SourceFailureLog log = new SourceFailureLog(6000);

        assertTrue(
                log.observe("graph", "Нет места", 10).orElseThrow().changed(),
                "Первый сбой источника печатается сразу: источник без лога чинят наугад");
    }

    @Test
    void observe_SameFailureNextRound_IsSilent() {
        SourceFailureLog log = new SourceFailureLog(6000);
        log.observe("graph", "Нет места", 10);

        assertEquals(
                Optional.empty(),
                log.observe("graph", "Нет места", 20),
                "Тот же сбой в следующей пересборке не печатается: иначе [incident] заливает лог каждые 5 секунд");
    }

    @Test
    void observe_SameFailureAfterWindow_ReturnsSummary() {
        SourceFailureLog log = new SourceFailureLog(100);
        log.observe("graph", "Нет места", 10);

        ChangeSummary.Summary summary = log.observe("graph", "Нет места", 200).orElseThrow();

        assertFalse(summary.changed(), "Сводка повторов не притворяется новым сбоем");
        assertEquals(2, summary.count(), "Сводка называет число падений источника за окно");
    }

    @Test
    void observe_NewMessage_IsPrintedImmediately() {
        SourceFailureLog log = new SourceFailureLog(6000);
        log.observe("graph", "Нет места", 10);

        assertTrue(
                log.observe("graph", "Другая ошибка", 20).orElseThrow().changed(),
                "Смена ошибки — новый сбой: её нельзя прятать за сводкой старой");
    }

    @Test
    void observe_DifferentSources_DoNotSuppressEachOther() {
        SourceFailureLog log = new SourceFailureLog(6000);
        log.observe("graph", "Нет места", 10);

        assertTrue(
                log.observe("rescue", "Нет места", 20).orElseThrow().changed(),
                "Сбои разных источников считаются отдельно: иначе сводка одного глушит первый сбой другого");
    }
}
