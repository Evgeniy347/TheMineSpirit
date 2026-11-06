package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ToolCraftTests {
    @Test
    void fulfilled_ToolInHands_Closes() {
        assertTrue(
                ToolCraft.fulfilled(
                        BoardCase.CRAFT_WOODEN_AXE, List.of("minecraft:jungle_planks", "minecraft:wooden_axe")),
                "Топор в инвентаре — задание крафта выполнено по существу");
        assertTrue(
                ToolCraft.fulfilled(BoardCase.CRAFT_WOODEN_AXE, List.of("wooden_axe")),
                "Голое имя предмета в наборе тоже означает, что топор у духа есть");
    }

    @Test
    void fulfilled_ToolAbsent_NotClosed() {
        assertFalse(
                ToolCraft.fulfilled(BoardCase.CRAFT_WOODEN_AXE, List.of("minecraft:jungle_planks", "minecraft:stick")),
                "Материалы вместо топора — не выполнение: задание крафта обязано дождаться инструмента, иначе доска "
                        + "закрывает его по чужому успеху");
        assertFalse(
                ToolCraft.fulfilled(BoardCase.CRAFT_WOODEN_PICKAXE, List.of("minecraft:wooden_axe")),
                "Топор не закрывает задание кирки: нужен именно предмет этого кейса");
    }

    @Test
    void fulfilled_NotToolCase_AlwaysCloses() {
        assertTrue(
                ToolCraft.fulfilled(BoardCase.GATHER_MATERIALS, List.of()),
                "Правило про инструмент касается только крафта инструментов: остальные задания закрываются как раньше");
    }
}
