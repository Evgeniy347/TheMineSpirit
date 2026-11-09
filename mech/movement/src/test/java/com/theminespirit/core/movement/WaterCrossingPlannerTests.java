package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WaterCrossingPlannerTests {
    @Test
    void cheapestAvailable_DirtThenCobblestone_ReturnsDirt() {
        Optional<String> chosen = WaterCrossingPlanner.cheapestAvailable(Map.of("cobblestone", 3, "dirt", 1));

        assertEquals(
                Optional.of("dirt"),
                chosen,
                "Из расходных блоков первым берут землю: порядок ToolBreakCost.CHEAP_ORDER делает выбор "
                        + "материала моста детерминированным");
    }

    @Test
    void cheapestAvailable_OnlyPlanks_ReturnsCheapestPlank() {
        Optional<String> chosen = WaterCrossingPlanner.cheapestAvailable(Map.of("oak_planks", 2));

        assertEquals(
                Optional.of("oak_planks"),
                chosen,
                "Без земли и камня мост строят из досок: доски — расходный материал, и дух не тащит "
                        + "для моста ценные блоки");
    }

    @Test
    void cheapestAvailable_CountsZero_ReturnsEmpty() {
        Optional<String> chosen = WaterCrossingPlanner.cheapestAvailable(Map.of("dirt", 0, "cobblestone", 0));

        assertTrue(
                chosen.isEmpty(),
                "Ноль расходных блоков даёт пустой выбор: из ничего мост не построить, вызывающий обязан "
                        + "уйти в другой способ переправы");
    }

    @Test
    void total_MixedStock_SumsAllTypes() {
        int total = WaterCrossingPlanner.total(Map.of(
                "dirt", 26,
                "gravel", 4,
                "jungle_planks", 8));

        assertEquals(
                38,
                total,
                "мост может быть смешанным: у spirit-4 observe25 не было 30 блоков одного вида, но было 38 "
                        + "расходных всего — считать надо весь запас, иначе мост отказывает на ровном месте");
    }

    @Test
    void total_EmptyStock_Zero() {
        assertEquals(0, WaterCrossingPlanner.total(Map.of()), "пустой инвентарь даёт ноль блоков на мост");
    }

    @Test
    void total_NegativeCount_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WaterCrossingPlanner.total(Map.of("dirt", -1)),
                "отрицательный запас — ошибка входа: складывать его с настоящими блоками значит врать о длине моста");
    }

    @Test
    void cheapestAvailable_NullMap_Throws() {
        assertThrows(
                NullPointerException.class,
                () -> WaterCrossingPlanner.cheapestAvailable(null),
                "Без карты наличия блоков выбор материала невозможен: null обязан падать, а не молча "
                        + "возвращать пустой мост");
    }
}
