package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WaterCrossingPlannerTests {
    @Test
    void swimMaxWidth_Constant_IsFour() {
        assertEquals(4, WaterCrossingPlanner.SWIM_MAX_WIDTH,
                "Порог заплыва — 4 блока воды: короче дух переплывает, шире предпочитает мост. "
                        + "Число задаёт /v1/capabilities и тест: осознанную смену переносить и туда");
    }

    @Test
    void choose_NarrowWater_Swims() {
        assertEquals(WaterCrossingPlanner.Method.SWIM, WaterCrossingPlanner.choose(2, 64),
                "Узкую воду переплывают: мост из блоков дороже времени и расходников, а заплыв короток");
    }

    @Test
    void choose_SwimMaxWidthWithBlocks_Swims() {
        assertEquals(WaterCrossingPlanner.Method.SWIM,
                WaterCrossingPlanner.choose(WaterCrossingPlanner.SWIM_MAX_WIDTH, 64),
                "Ровно на пороге (4 блока) ещё плывут: граница включена в заплыв, иначе тесная вода "
                        + "заставляла бы строить мост из-за одного блока");
    }

    @Test
    void choose_OneWiderThanSwimMaxWithEnoughBlocks_Bridges() {
        assertEquals(WaterCrossingPlanner.Method.BRIDGE, WaterCrossingPlanner.choose(5, 5),
                "Воду шире порога и с запасом блоков переходят мостом: заплыв через пять клеток "
                        + "рискует воздухом");
    }

    @Test
    void choose_OneWiderThanSwimMaxWithoutEnoughBlocks_Swims() {
        assertEquals(WaterCrossingPlanner.Method.SWIM, WaterCrossingPlanner.choose(5, 4),
                "Блоков меньше ширины — мост не достроить, поэтому дух плывёт: четыре блока на пять "
                        + "клеток обрывались бы на середине");
    }

    @Test
    void choose_WideWaterWithEnoughBlocks_Bridges() {
        assertEquals(WaterCrossingPlanner.Method.BRIDGE, WaterCrossingPlanner.choose(12, 20),
                "Широкую воду с достатком блоков переходят мостом: долгий заплыв тратит воздух");
    }

    @Test
    void choose_ZeroWidth_Throws() {
        assertThrows(IllegalArgumentException.class, () -> WaterCrossingPlanner.choose(0, 16),
                "Нулевая ширина воды обязана отвергаться: переправы нет, и молчаливый выбор способа "
                        + "запутал бы итог задачи");
    }

    @Test
    void choose_NegativeBlocks_Throws() {
        assertThrows(IllegalArgumentException.class, () -> WaterCrossingPlanner.choose(8, -1),
                "Отрицательное число блоков обязано отвергаться: такого не бывает, это ошибка вызова");
    }

    @Test
    void cheapestAvailable_DirtThenCobblestone_ReturnsDirt() {
        Optional<String> chosen = WaterCrossingPlanner.cheapestAvailable(Map.of("cobblestone", 3, "dirt", 1));

        assertEquals(Optional.of("dirt"), chosen,
                "Из расходных блоков первым берут землю: порядок ToolBreakCost.CHEAP_ORDER делает выбор "
                        + "материала моста детерминированным");
    }

    @Test
    void cheapestAvailable_OnlyPlanks_ReturnsCheapestPlank() {
        Optional<String> chosen = WaterCrossingPlanner.cheapestAvailable(Map.of("oak_planks", 2));

        assertEquals(Optional.of("oak_planks"), chosen,
                "Без земли и камня мост строят из досок: доски — расходный материал, и дух не тащит "
                        + "для моста ценные блоки");
    }

    @Test
    void cheapestAvailable_CountsZero_ReturnsEmpty() {
        Optional<String> chosen = WaterCrossingPlanner.cheapestAvailable(Map.of("dirt", 0, "cobblestone", 0));

        assertTrue(chosen.isEmpty(),
                "Ноль расходных блоков даёт пустой выбор: из ничего мост не построить, вызывающий обязан "
                        + "уйти в другой способ переправы");
    }

    @Test
    void cheapestAvailable_NullMap_Throws() {
        assertThrows(NullPointerException.class, () -> WaterCrossingPlanner.cheapestAvailable(null),
                "Без карты наличия блоков выбор материала невозможен: null обязан падать, а не молча "
                        + "возвращать пустой мост");
    }
}
