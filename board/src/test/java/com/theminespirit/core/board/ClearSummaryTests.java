package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClearSummaryTests {
    @Test
    void cleared_FirstBox_IsFirstLine() {
        ClearSummary summary = new ClearSummary(6000);

        ClearSummary.Summary first = summary.cleared(3, 10).orElseThrow();

        assertTrue(
                first.first(),
                "Первая расчистка печатается полной строкой: по ней видят, что [clear] работает"
                        + " Иначе тест cleared_FirstBox_IsFirstLine перестанет ловить эту поломку. При смене cleared_FirstBox_IsFirstLine проверь это утверждение.");
        assertEquals(
                1,
                first.boxes(),
                "Первая строка считает один ящик"
                        + " Иначе тест cleared_FirstBox_IsFirstLine перестанет ловить эту поломку. При смене cleared_FirstBox_IsFirstLine проверь это утверждение.");
        assertEquals(
                3,
                first.removed(),
                "Первая строка считает убранные блоки"
                        + " Иначе тест cleared_FirstBox_IsFirstLine перестанет ловить эту поломку. При смене cleared_FirstBox_IsFirstLine проверь это утверждение.");
    }

    @Test
    void cleared_RepeatsInsideWindow_AreSilent() {
        ClearSummary summary = new ClearSummary(6000);
        summary.cleared(3, 10);

        assertEquals(
                Optional.empty(),
                summary.cleared(1, 20),
                "Вторая и следующие клетки внутри окна не печатаются: именно они давали ~300 строк за 20 минут"
                        + " Иначе тест cleared_RepeatsInsideWindow_AreSilent перестанет ловить эту поломку. При смене cleared_RepeatsInsideWindow_AreSilent проверь это утверждение.");
    }

    @Test
    void cleared_AfterWindow_ReturnsOneAggregateLine() {
        ClearSummary summary = new ClearSummary(100);
        summary.cleared(3, 10);
        summary.cleared(2, 20);

        ClearSummary.Summary aggregate = summary.cleared(1, 200).orElseThrow();

        assertFalse(
                aggregate.first(),
                "Сводка повторов не притворяется первой строкой"
                        + " Иначе тест cleared_AfterWindow_ReturnsOneAggregateLine перестанет ловить эту поломку. При смене cleared_AfterWindow_ReturnsOneAggregateLine проверь это утверждение.");
        assertEquals(
                3,
                aggregate.boxes(),
                "Сводка называет число очищенных ящиков за окно"
                        + " Иначе тест cleared_AfterWindow_ReturnsOneAggregateLine перестанет ловить эту поломку. При смене cleared_AfterWindow_ReturnsOneAggregateLine проверь это утверждение.");
        assertEquals(
                6,
                aggregate.removed(),
                "Сводка называет общее число убранных блоков за окно"
                        + " Иначе тест cleared_AfterWindow_ReturnsOneAggregateLine перестанет ловить эту поломку. При смене cleared_AfterWindow_ReturnsOneAggregateLine проверь это утверждение.");
    }

    @Test
    void empty_EmptyBox_IsCountedSeparately() {
        ClearSummary summary = new ClearSummary(100);
        summary.cleared(3, 10);
        summary.empty(20);

        ClearSummary.Summary aggregate = summary.empty(200).orElseThrow();

        assertEquals(
                3,
                aggregate.boxes(),
                "Пустой ящик тоже считается очищенным"
                        + " Иначе тест empty_EmptyBox_IsCountedSeparately перестанет ловить эту поломку. При смене empty_EmptyBox_IsCountedSeparately проверь это утверждение.");
        assertEquals(
                2,
                aggregate.empty(),
                "Сводка отдельно называет пустые ящики"
                        + " Иначе тест empty_EmptyBox_IsCountedSeparately перестанет ловить эту поломку. При смене empty_EmptyBox_IsCountedSeparately проверь это утверждение.");
        assertEquals(
                3,
                aggregate.removed(),
                "Пустой ящик не добавляет убранных блоков"
                        + " Иначе тест empty_EmptyBox_IsCountedSeparately перестанет ловить эту поломку. При смене empty_EmptyBox_IsCountedSeparately проверь это утверждение.");
    }

    @Test
    void cleared_NegativeRemoved_IsRejected() {
        ClearSummary summary = new ClearSummary(100);

        assertEquals(
                "Убрано -1 блоков: счёт не бывает отрицательным",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> summary.cleared(-1, 10),
                                "Отрицательный счёт — ошибка вызывающего: иначе сводка врёт о работе"
                                        + " При смене cleared_NegativeRemoved_IsRejected проверь это утверждение.")
                        .getMessage(),
                "Отрицательный счёт — ошибка вызывающего: иначе сводка врёт о работе"
                        + " При смене cleared_NegativeRemoved_IsRejected проверь это утверждение.");
    }
}
