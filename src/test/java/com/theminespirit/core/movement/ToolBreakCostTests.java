package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ToolBreakCostTests {
    @Test
    void throwaway_KnownIds_AreListed() {
        for (String id : List.of("dirt", "cobblestone", "gravel", "sand", "oak_planks")) {
            assertTrue(
                    ToolBreakCost.CHEAP_ORDER.contains(id),
                    "Расходный блок '" + id + "' обязан быть в списке CHEAP_ORDER: без него засыпка и мосты "
                            + "пойдут ценными блоками");
        }
    }
}
