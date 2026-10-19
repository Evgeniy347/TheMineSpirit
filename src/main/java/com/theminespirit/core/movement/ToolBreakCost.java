package com.theminespirit.core.movement;

import java.util.List;

public final class ToolBreakCost {
    // Порядок расходных блоков: земля и камень первыми. Засыпка и мосты берут первый
    // доступный по этому порядку, поэтому он же задаёт детерминированный выбор материала.
    public static final List<String> CHEAP_ORDER = List.of(
            "dirt",
            "cobblestone",
            "gravel",
            "sand",
            "oak_planks",
            "spruce_planks",
            "birch_planks",
            "jungle_planks",
            "acacia_planks",
            "dark_oak_planks",
            "mangrove_planks",
            "cherry_planks",
            "bamboo_planks",
            "crimson_planks",
            "warped_planks");

    private ToolBreakCost() {}
}
