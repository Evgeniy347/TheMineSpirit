package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ToolGateTests {
    @Test
    void allows_ClearWithoutPickaxe_NotGiven() {
        assertFalse(
                ToolGate.allows(BoardCase.CLEAR_GROUNDS, List.of("minecraft:wooden_shovel", "minecraft:stone_sword")),
                "Расчистка с выравниванием режет камень: духу без кирки она не выдаётся, а не проваливается «нужен "
                        + "инструмент» (INC-67)");
    }

    @Test
    void allows_ClearWithStonePickaxe_Given() {
        assertTrue(
                ToolGate.allows(BoardCase.CLEAR_GROUNDS, List.of("minecraft:stone_pickaxe")),
                "Любая кирка годится для расчистки");
        assertTrue(ToolGate.allows(BoardCase.STOCK_CHESTS, List.of()), "Кейсу без инструмента гейт не мешает");
    }
}
