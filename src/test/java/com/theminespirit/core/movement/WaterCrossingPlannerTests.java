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
