package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class FuelTableTests {
    @ParameterizedTest(name = "{0} горит {1} тиков")
    @CsvSource({"coal, 1600", "charcoal, 1600", "oak_planks, 300", "oak_log, 300"})
    void burnTicks_VanillaFuel_MatchesVanillaBurnTime(String item, int ticks) {
        assertEquals(ticks, FuelTable.burnTicks(item),
                "Время горения обязано совпадать с ванилью 1.21.1 (уголь и древесный уголь 1600 тиков = 8 предметов, "
                        + "доска и бревно 300 тиков = 1.5 предмета): иначе резолвер спланирует мало топлива и печь "
                        + "встанет посреди плавки. Если ваниль поменяла значения — обновите таблицу и этот тест");
    }

    @Test
    void burnTicks_UnknownItem_ReturnsZero() {
        assertEquals(0, FuelTable.burnTicks("iron_ore"),
                "Неизвестное топливо обязано давать 0 тиков: иначе резолвер попробует топить рудой и спланирует "
                        + "провал вместо UnplannableGoalException");
    }

    @Test
    void preferenceOrder_FuelTable_CoalFirstLogsBeforePlanks() {
        assertEquals(List.of("coal", "charcoal", "oak_log", "oak_planks"), FuelTable.preferenceOrder(),
                "Порядок предпочтения: сначала ёмкое топливо (уголь и древесный уголь по 8 предметов), затем "
                        + "добываемое напрямую (брёвна), затем крафтовое (доски): иначе дух таскает лишнее топливо");
    }

    @ParameterizedTest(name = "{0} предмета при горении {1} тиков требуют {2} топлива")
    @MethodSource("fuelNeeds")
    void fuelNeeded_Table_CeilsCookTicksOverBurnTicks(int count, int burn, int expected) {
        assertEquals(expected, FuelTable.fuelNeeded(count, burn),
                "Топлива берётся ровно столько, сколько нужно: ceil(count × 200 / burn). Недобор остановит печь, "
                        + "перебор оставит духа без дерева. Числа — ваниль 1.21.1, при смене версии пересчитать");
    }

    static Stream<Arguments> fuelNeeds() {
        return Stream.of(
                Arguments.of(8, 1600, 1),
                Arguments.of(9, 1600, 2),
                Arguments.of(1, 1600, 1),
                Arguments.of(2, 1600, 1),
                Arguments.of(3, 1600, 1),
                Arguments.of(8, 300, 6),
                Arguments.of(3, 300, 2),
                Arguments.of(1, 300, 1),
                Arguments.of(1, 100, 2),
                Arguments.of(64, 300, 43));
    }

    @Test
    void fuelNeeded_ZeroCount_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> FuelTable.fuelNeeded(0, 1600),
                "Ноль предметов обязан отвергаться: иначе план получит шаг добычи нуля топлива");
    }

    @Test
    void fuelNeeded_ZeroBurn_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> FuelTable.fuelNeeded(3, 0),
                "Нулевое горение обязано отвергаться: деление на ноль даст бесконечность вместо топлива");
    }
}
