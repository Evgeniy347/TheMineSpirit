package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.DescentData;
import com.theminespirit.core.mining.DescentDirection;
import com.theminespirit.core.mining.SafeMineMode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SafeMineParamsTests {
    @Test
    void from_ModeAndDirectionOnly_FillsDefaultsFromData() {
        SafeMineParams params = SafeMineParams.from(Map.of("mode", "staircase", "direction", "east"));

        assertEquals(SafeMineMode.STAIRCASE, params.mode(), "Форма обязана читаться из обязательного параметра");
        assertEquals(DescentDirection.EAST, params.direction(), "Направление обязано читаться из параметра");
        assertEquals(DescentData.loadDefault().targetY(), params.y(),
                "Целевой этаж по умолчанию — из данных: числа не дублируются в коде");
        assertEquals(CatalogLimits.MINE_LENGTH_DEFAULT, params.length(),
                "Длина штольни по умолчанию — из каталога, а не из шаблона fallback");
        assertEquals(DescentData.loadDefault().torchInterval(), params.torchInterval(),
                "Интервал факелов по умолчанию — из данных шахты: шахта освещается по таблице, а не константой");
    }

    @Test
    void from_WithoutMode_Throws() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SafeMineParams.from(Map.of("direction", "east")),
                "Без формы шахты параметры обязаны отклоняться: неизвестно, копать вниз или вбок");

        assertTrue(error.getMessage().contains("mode"), "Отказ обязан называть пропущенный параметр mode");
    }

    @Test
    void from_UnknownMode_Throws() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SafeMineParams.from(Map.of("mode", "spiral", "direction", "east")),
                "Неизвестная форма шахты обязана отклоняться до старта");

        assertTrue(error.getMessage().contains("spiral"), "Отказ обязан называть неизвестную форму");
    }

    @Test
    void from_LengthOutOfRange_Throws() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SafeMineParams.from(Map.of("mode", "tunnel", "direction", "east", "length", 0)),
                "Длина штольни вне диапазона обязана отклоняться до чтения мира");

        assertTrue(error.getMessage().contains("Длина штольни"), "Отказ обязан называть длину штольни");
    }

    @Test
    void from_ExplicitValues_OverrideDefaults() {
        SafeMineParams params = SafeMineParams.from(Map.of(
                "mode", "tunnel", "direction", "north", "y", -59, "length", 10, "torchInterval", 5));

        assertEquals(SafeMineMode.TUNNEL, params.mode(), "Явная форма обязана победить умолчание");
        assertEquals(10, params.length(), "Явная длина обязана победить умолчание каталога");
        assertEquals(5, params.torchInterval(), "Явный интервал факелов обязан победить данные");
        assertEquals(-59, params.y(), "Явный целевой этаж обязан победить данные");
    }

    @Test
    void from_TorchIntervalOutOfRange_Throws() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SafeMineParams.from(Map.of("mode", "tunnel", "direction", "east", "torchInterval", 100)),
                "Интервал факелов вне диапазона обязан отклоняться до чтения мира");

        assertTrue(error.getMessage().contains("Интервал факелов"), "Отказ обязан называть интервал факелов");
    }
}
