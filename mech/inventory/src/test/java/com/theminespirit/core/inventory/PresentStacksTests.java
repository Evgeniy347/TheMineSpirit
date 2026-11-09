package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PresentStacksTests {
    private static final Stack BREAD = new Stack("minecraft:bread", 0, 3, 64);
    private static final Stack COAL = new Stack("minecraft:coal", 1, 7, 64);

    @Test
    void of_EmptySlots_AreDroppedAndOrderKept() {
        List<Stack> slots = Arrays.asList(BREAD, null, COAL, null);

        assertEquals(
                List.of(BREAD, COAL),
                PresentStacks.of(slots),
                "Пустой слот инвентаря не стопка: иначе перенос считал бы воздух предметом и искал получателя");
    }

    @Test
    void of_AllSlotsEmpty_IsEmpty() {
        assertEquals(
                List.of(),
                PresentStacks.of(Arrays.asList(null, null, null)),
                "Пустой инвентарь даёт пустой список: поиск места не должен видеть фантомные стопки");
    }

    @Test
    void of_NoEmptySlots_ReturnsSameStacks() {
        assertEquals(
                List.of(BREAD, COAL),
                PresentStacks.of(List.of(BREAD, COAL)),
                "Заполненный инвентарь переносится целиком, без перестановок");
    }
}
