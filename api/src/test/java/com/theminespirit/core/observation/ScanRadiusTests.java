package com.theminespirit.core.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScanRadiusTests {
    @Test
    void constructor_MinAndMax_AreAccepted() {
        assertEquals(
                ScanRadius.MIN,
                new ScanRadius(ScanRadius.MIN).value(),
                "радиус, равный минимальному лимиту, обязан приниматься без изменения: лимит — это граница, а не запрет"
                        + " Иначе тест constructor_MinAndMax_AreAccepted перестанет ловить эту поломку. При смене constructor_MinAndMax_AreAccepted проверь это утверждение.");
        assertEquals(
                ScanRadius.MAX,
                new ScanRadius(ScanRadius.MAX).value(),
                "радиус, равный максимальному лимиту, обязан приниматься без изменения: лимит — это граница, а не запрет"
                        + " Иначе тест constructor_MinAndMax_AreAccepted перестанет ловить эту поломку. При смене constructor_MinAndMax_AreAccepted проверь это утверждение.");
    }

    @Test
    void constructor_BelowMin_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new ScanRadius(ScanRadius.MIN - 1),
                "радиус меньше лимита обязан отклоняться: пустой скан бессмыслен"
                        + " Иначе тест constructor_BelowMin_IsRejected перестанет ловить эту поломку. При смене constructor_BelowMin_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains(Integer.toString(ScanRadius.MIN)),
                "отказ обязан называть нижнюю границу радиуса. Фактическое: " + error.getMessage()
                        + " Иначе тест constructor_BelowMin_IsRejected перестанет ловить эту поломку. При смене constructor_BelowMin_IsRejected проверь это утверждение.");
    }

    @Test
    void constructor_AboveMax_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new ScanRadius(ScanRadius.MAX + 1),
                "радиус больше лимита обязан отклоняться: скан такого размера пересобирает мир по блокам"
                        + " Иначе тест constructor_AboveMax_IsRejected перестанет ловить эту поломку. При смене constructor_AboveMax_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains(Integer.toString(ScanRadius.MAX)),
                "отказ обязан называть верхнюю границу радиуса. Фактическое: " + error.getMessage()
                        + " Иначе тест constructor_AboveMax_IsRejected перестанет ловить эту поломку. При смене constructor_AboveMax_IsRejected проверь это утверждение.");
    }

    @Test
    void defaultRadius_PassedToConstructor_LiesWithinLimits() {
        assertTrue(
                ScanRadius.DEFAULT.value() >= ScanRadius.MIN && ScanRadius.DEFAULT.value() <= ScanRadius.MAX,
                "радиус по умолчанию обязан лежать в допустимых границах: иначе команда observe без аргумента не работает"
                        + " При смене defaultRadius_PassedToConstructor_LiesWithinLimits проверь это утверждение.");
        assertEquals(
                ScanRadius.DEFAULT,
                new ScanRadius(ScanRadius.DEFAULT.value()),
                "конструктор обязан принимать значение по умолчанию: DEFAULT — валидный радиус"
                        + " Иначе тест defaultRadius_PassedToConstructor_LiesWithinLimits перестанет ловить эту поломку. При смене defaultRadius_PassedToConstructor_LiesWithinLimits проверь это утверждение.");
    }
}
