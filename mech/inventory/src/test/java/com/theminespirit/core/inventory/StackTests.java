package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StackTests {
    @Test
    void of_CountAboveMaxStack_ThrowsWithBothNumbers() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new Stack("cobblestone", 0, 65, 64),
                "Стопка больше максимума слота обязана быть отказом: 65 булыжника в слоте на 64 "
                        + "не помещаются в игре, и такая стопка роняла бы предметы при переносе, "
                        + "иначе инвентарь теряет предметы. "
                        + "При смене конструктора Stack проверь границу 65 и 64");

        assertTrue(
                error.getMessage().contains("65") && error.getMessage().contains("64"),
                "Сообщение обязано называть количество 65 и максимум 64: по ним находят, какой слот "
                        + "переполнен, иначе причину не найти. Фактическое: " + error.getMessage()
                        + ". При смене текста ошибки проверь оба числа");
    }

    @ParameterizedTest(name = "{0} × {1} из {2} собирается")
    @CsvSource({"cobblestone, 1, 64", "cobblestone, 64, 64", "iron_pickaxe, 1, 1", "torch, 16, 64"})
    void of_CountWithinMaxStack_Accepts(String item, int count, int maxStack) {
        Stack stack = new Stack(item, 0, count, maxStack);

        assertEquals(
                count,
                stack.count(),
                "Стопка " + count + " из " + maxStack + " обязана собираться: граница — только строго "
                        + "больше максимума, иначе полный слот стал бы ошибкой. При смене проверки проверь 64 из 64");
    }

    @Test
    void freeSpace_FullStack_ReturnsZero() {
        Stack stack = new Stack("cobblestone", 0, 64, 64);

        assertEquals(
                0,
                stack.freeSpace(),
                "Полная стопка не принимает ничего: планировщик переноса опирается на это, выбирая, "
                        + "куда доливать, иначе он выберет уже полный слот. При смене freeSpace проверь 64 из 64");
    }
}
