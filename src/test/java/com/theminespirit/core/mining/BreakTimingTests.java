package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BreakTimingTests {
    @ParameterizedTest(name = "{0}: твёрдость {1}, скорость {2}, правильный {3} = {4} тиков")
    @CsvSource({
            "бревно рукой, 2.0, 1.0, true, 60",
            "бревно железным топором, 2.0, 6.0, true, 10",
            "камень деревянной киркой, 1.5, 2.0, true, 23",
            "камень рукой без кирки, 1.5, 1.0, false, 150",
            "камень железной киркой, 1.5, 6.0, true, 8",
            "земля рукой, 0.5, 1.0, true, 15",
            "земля железной лопатой, 0.5, 6.0, true, 3",
            "листва рукой, 0.2, 1.0, true, 6",
            "блок нулевой твёрдости, 0.0, 1.0, true, 1",
            "обсидиан алмазной киркой, 50.0, 8.0, true, 188",
            "обсидиан рукой, 50.0, 1.0, false, 5000"})
    void ticks_VanillaTable_MatchesFormula(String description, double hardness, double speed, boolean correct,
            int expected) {
        assertEquals(expected, BreakTiming.ticks(hardness, speed, correct),
                description + ": срок ломки должен совпадать с ванильным расчётом игрока "
                        + "ceil(твёрдость × (30 или 100) / скорость), иначе дух ломает быстрее или медленнее игрока");
    }

    @Test
    void ticks_NegativeHardness_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> BreakTiming.ticks(-1.0, 1.0, true),
                "Неразрушимый блок (твёрдость -1) ломать нельзя: срок обязан отвергаться, а не становиться 1 тиком");

        assertTrue(error.getMessage().contains("-1.0"),
                "Сообщение обязано называть твёрдость. Фактическое: " + error.getMessage());
    }

    @Test
    void ticks_NonPositiveSpeed_IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> BreakTiming.ticks(1.0, 0.0, true),
                "Скорость 0 обязана отвергаться: деление на ноль дало бы бесконечный срок");
        assertThrows(IllegalArgumentException.class, () -> BreakTiming.ticks(1.0, -2.0, true),
                "Отрицательная скорость обязана отвергаться: срок был бы отрицательным");
    }

    @Test
    void ticks_HugeHardnessTinySpeed_SaturatesInsteadOfOverflowing() {
        assertEquals(Integer.MAX_VALUE, BreakTiming.ticks(1.0e300, 1.0e-300, false),
                "Огромный срок обязан упираться в Integer.MAX_VALUE: переполнение int дало бы отрицательный срок");
    }
}
