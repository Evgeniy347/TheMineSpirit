package com.theminespirit.core.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProcessingStockTests {
    @Test
    void plankProduct_LogWithMostCount_ChosenSpecies() {
        ProcessingStock stock =
                new ProcessingStock(0, 0, 0, 0, Map.of(), Map.of("minecraft:oak_log", 3, "minecraft:birch_log", 9));

        assertEquals(
                Optional.of("minecraft:birch_planks"),
                stock.plankProduct(),
                "Доски делают из породы, которой больше: иначе дух тратит редкое бревно, оставляя частого соседа"
                        + " При смене plankProduct_LogWithMostCount_ChosenSpecies проверь это утверждение.");
    }

    @Test
    void plankProduct_StrippedAndWoodVariants_SamePlanks() {
        assertEquals(
                Optional.of("minecraft:oak_planks"),
                ProcessingStock.planksOf("minecraft:stripped_oak_log"),
                "Ошкуренное бревно даёт доски дуба: иначе дух считает сырьё пропащим и не крафтит доски"
                        + " При смене plankProduct_StrippedAndWoodVariants_SamePlanks проверь это утверждение.");
        assertEquals(
                Optional.of("minecraft:crimson_planks"),
                ProcessingStock.planksOf("minecraft:crimson_stem"),
                "Стебель багрового дерева даёт его доски: порода узнаётся по суффиксу, а не только по _log"
                        + " Иначе тест plankProduct_StrippedAndWoodVariants_SamePlanks перестанет ловить эту поломку. При смене plankProduct_StrippedAndWoodVariants_SamePlanks проверь это утверждение.");
    }

    @Test
    void plankProduct_NoLogs_Empty() {
        assertEquals(
                Optional.empty(),
                ProcessingStock.NONE.plankProduct(),
                "Без брёвен породу досок взять неоткуда: пустой снимок не выдумывает вид дерева"
                        + " Иначе тест plankProduct_NoLogs_Empty перестанет ловить эту поломку. При смене plankProduct_NoLogs_Empty проверь это утверждение.");
    }

    @Test
    void hasRawFoodAndLogs_Empty_ToFalse() {
        assertFalse(
                ProcessingStock.NONE.hasRawFood(),
                "Пустая опись сырой еды означает её отсутствие: иначе готовка востребована на пустом месте"
                        + " При смене hasRawFoodAndLogs_Empty_ToFalse проверь это утверждение.");
        assertFalse(
                ProcessingStock.NONE.hasLogs(),
                "Пустая опись брёвен означает их отсутствие: иначе крафт досок идёт без сырья"
                        + " При смене hasRawFoodAndLogs_Empty_ToFalse проверь это утверждение.");
    }

    @Test
    void constructor_NegativeAndNull_Throw() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProcessingStock(-1, 0, 0, 0, Map.of(), Map.of()),
                "Отрицательный запас ломает дефицит: запасы не бывают меньше нуля"
                        + " При смене constructor_NegativeAndNull_Throw проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProcessingStock(0, 0, 0, 0, null, Map.of()),
                "Опись сырой еды null не подсчитать: пустой набор задают пустой картой"
                        + " Иначе тест constructor_NegativeAndNull_Throw перестанет ловить эту поломку. При смене constructor_NegativeAndNull_Throw проверь это утверждение.");
        Map<String, Integer> blank = new HashMap<>();
        blank.put(" ", 1);
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProcessingStock(0, 0, 0, 0, blank, Map.of()),
                "Предмет без имени не опознать: счёт идёт по id предметов"
                        + " Иначе тест constructor_NegativeAndNull_Throw перестанет ловить эту поломку. При смене constructor_NegativeAndNull_Throw проверь это утверждение.");
    }

    @Test
    void stock_ValidCounts_KeepCounts() {
        ProcessingStock stock =
                new ProcessingStock(4, 8, 2, 12.0, Map.of("minecraft:beef", 3), Map.of("minecraft:oak_log", 5));

        assertTrue(
                stock.charcoal() == 4 && stock.planks() == 8 && stock.sticks() == 2,
                "Запас переработки хранит уголь, доски и палки раздельно: иначе спрос не различить по видам"
                        + " При смене stock_ValidCounts_KeepCounts проверь это утверждение.");
    }
}
