package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChainRowsTests {
    @Test
    void rows_TotalBelowCapacity_ShowsAll() {
        ChainRows rows = ChainRows.of(2, 3);
        assertEquals(
                2,
                rows.shown(),
                "Две строки в свободном месте обязаны показываться целиком: иначе «×2 доски» пропадает из цепочки без причины. При смене rows_TotalBelowCapacity_ShowsAll проверь это утверждение.");
        assertFalse(
                rows.more(),
                "При полном показе хвост «…ещё N» не нужен: иначе игрок ищет скрытые строки, которых нет. При смене rows_TotalBelowCapacity_ShowsAll проверь это утверждение.");
    }

    @Test
    void rows_MoreThanLimit_KeepsThreeAndReportsRest() {
        ChainRows rows = ChainRows.of(5, 6);
        assertEquals(
                3,
                rows.shown(),
                "Группа показывает не больше трёх строк: иначе панель цепочки закроет кнопки «Сделать». При смене rows_MoreThanLimit_KeepsThreeAndReportsRest проверь это утверждение.");
        assertEquals(
                2,
                rows.hidden(),
                "Хвост обязан назвать остаток, иначе игрок решит, что в списке всего три строки, а не пять. При смене rows_MoreThanLimit_KeepsThreeAndReportsRest проверь это утверждение.");
        assertTrue(
                rows.more(),
                "Флаг «есть ещё» обязан включаться при скрытых строках: иначе «…ещё N» не нарисуется. При смене rows_MoreThanLimit_KeepsThreeAndReportsRest проверь это утверждение.");
    }

    @Test
    void rows_NoSpace_ReportsAllHidden() {
        ChainRows rows = ChainRows.of(4, 0);
        assertEquals(
                0,
                rows.shown(),
                "Без места строки не рисуются: иначе текст вылезет под нижнюю границу панели. При смене rows_NoSpace_ReportsAllHidden проверь это утверждение.");
        assertEquals(
                4,
                rows.hidden(),
                "Без места весь список уходит в «…ещё N»: иначе игрок не узнает, что материалы вообще есть. При смене rows_NoSpace_ReportsAllHidden проверь это утверждение.");
    }

    @Test
    void of_NegativeTotal_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChainRows.of(-1, 3),
                "Отрицательное число строк обязано падать: иначе ошибка расчёта молча покажет пустую цепочку без предупреждения. При смене of_NegativeTotal_IsRejected проверь это утверждение.");
    }
}
