package com.theminespirit.core.observation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScanRadiusTests {
    @Test
    void constructor_MinAndMax_AreAccepted() {
        assertDoesNotThrow(() -> new ScanRadius(ScanRadius.MIN),
                "радиус, равный минимальному лимиту, обязан приниматься: лимит — это граница, а не запрет");
        assertDoesNotThrow(() -> new ScanRadius(ScanRadius.MAX),
                "радиус, равный максимальному лимиту, обязан приниматься: лимит — это граница, а не запрет");
    }

    @Test
    void constructor_BelowMin_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ScanRadius(ScanRadius.MIN - 1),
                "радиус меньше лимита обязан отклоняться: пустой скан бессмыслен");

        assertTrue(error.getMessage().contains(Integer.toString(ScanRadius.MIN)),
                "отказ обязан называть нижнюю границу радиуса. Фактическое: " + error.getMessage());
    }

    @Test
    void constructor_AboveMax_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ScanRadius(ScanRadius.MAX + 1),
                "радиус больше лимита обязан отклоняться: скан такого размера пересобирает мир по блокам");

        assertTrue(error.getMessage().contains(Integer.toString(ScanRadius.MAX)),
                "отказ обязан называть верхнюю границу радиуса. Фактическое: " + error.getMessage());
    }

    @Test
    void defaultRadius_WithinLimits() {
        assertTrue(ScanRadius.DEFAULT.value() >= ScanRadius.MIN && ScanRadius.DEFAULT.value() <= ScanRadius.MAX,
                "радиус по умолчанию обязан лежать в допустимых границах: иначе команда observe без аргумента не работает");
        assertEquals(ScanRadius.DEFAULT, new ScanRadius(ScanRadius.DEFAULT.value()),
                "конструктор обязан принимать значение по умолчанию: DEFAULT — валидный радиус");
    }
}