package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolStockTests {
    @Test
    void woodenShortages_EmptyStockTwoSets_AllThreeShortByTwo() {
        assertEquals(
                Map.of(
                        BoardCase.CRAFT_WOODEN_PICKAXE, 2,
                        BoardCase.CRAFT_WOODEN_AXE, 2,
                        BoardCase.CRAFT_WOODEN_SHOVEL, 2),
                ToolSet.woodenShortages(Map.of(), 2),
                "Пустой запас на двух духов — по два деревянных инструмента каждого вида: постоянный запас набора "
                        + "поддерживается сам, а не только первыми шагами");
    }

    @Test
    void woodenShortages_StonePickaxeCountsForWooden_OnlyAxeAndShovelShort() {
        assertEquals(
                Map.of(BoardCase.CRAFT_WOODEN_AXE, 1, BoardCase.CRAFT_WOODEN_SHOVEL, 1),
                ToolSet.woodenShortages(Map.of("stone_pickaxe", 1), 1),
                "Каменная кирка заменяет деревянную: дух с лучшим инструментом деревянный не ждёт");
    }

    @Test
    void woodenShortages_PickaxeNotCountedAsAxe_AxeShort() {
        assertEquals(
                Map.of(BoardCase.CRAFT_WOODEN_AXE, 1, BoardCase.CRAFT_WOODEN_SHOVEL, 1),
                ToolSet.woodenShortages(Map.of("wooden_pickaxe", 1), 1),
                "Кирка (pickaxe) не засчитывается за топор (axe): совпадение суффикса не делает её топором");
    }

    @Test
    void woodenShortages_FullSet_Empty() {
        assertEquals(
                Map.of(),
                ToolSet.woodenShortages(Map.of("wooden_pickaxe", 1, "iron_axe", 1, "wooden_shovel", 3), 1),
                "Полный набор снимает задания запаса: иначе доска крафтит инструменты бесконечно");
    }
}
