package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolCraftTests {
    @Test
    void fulfilled_ToolInHands_Closes() {
        assertTrue(
                ToolCraft.fulfilled(
                        BoardCase.CRAFT_WOODEN_AXE, List.of("minecraft:jungle_planks", "minecraft:wooden_axe")),
                "Топор в инвентаре — задание крафта выполнено по существу"
                        + " Иначе тест fulfilled_ToolInHands_Closes перестанет ловить эту поломку. При смене fulfilled_ToolInHands_Closes проверь это утверждение.");
        assertTrue(
                ToolCraft.fulfilled(BoardCase.CRAFT_WOODEN_AXE, List.of("wooden_axe")),
                "Голое имя предмета в наборе тоже означает, что топор у духа есть"
                        + " Иначе тест fulfilled_ToolInHands_Closes перестанет ловить эту поломку. При смене fulfilled_ToolInHands_Closes проверь это утверждение.");
    }

    @Test
    void fulfilled_ToolAbsent_NotClosed() {
        assertFalse(
                ToolCraft.fulfilled(BoardCase.CRAFT_WOODEN_AXE, List.of("minecraft:jungle_planks", "minecraft:stick")),
                "Материалы вместо топора — не выполнение: задание крафта обязано дождаться инструмента, иначе доска "
                        + "закрывает его по чужому успеху"
                        + " При смене fulfilled_ToolAbsent_NotClosed проверь это утверждение.");
        assertFalse(
                ToolCraft.fulfilled(BoardCase.CRAFT_WOODEN_PICKAXE, List.of("minecraft:wooden_axe")),
                "Топор не закрывает задание кирки: нужен именно предмет этого кейса"
                        + " Иначе тест fulfilled_ToolAbsent_NotClosed перестанет ловить эту поломку. При смене fulfilled_ToolAbsent_NotClosed проверь это утверждение.");
    }

    @Test
    void fulfilled_NotToolCase_AlwaysCloses() {
        assertTrue(
                ToolCraft.fulfilled(BoardCase.GATHER_MATERIALS, List.of()),
                "Правило про инструмент касается только крафта инструментов: остальные задания закрываются как раньше"
                        + " Иначе тест fulfilled_NotToolCase_AlwaysCloses перестанет ловить эту поломку. При смене fulfilled_NotToolCase_AlwaysCloses проверь это утверждение.");
    }
}
