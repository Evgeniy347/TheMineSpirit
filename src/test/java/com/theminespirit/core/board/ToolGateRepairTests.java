package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolGateRepairTests {
    private static final BoardTicket MINE = RepairPlan.ticket(BoardCase.BUILD_MINE, Map.of("cobblestone", 56), 1);
    private static final BoardTicket HOUSE = RepairPlan.ticket(BoardCase.BUILD_HOUSE, Map.of("jungle_log", 1), 1);

    @Test
    void ticket_MineRepair_RequiresPickaxeAndCobblestone() {
        assertTrue(MINE.modifiers().contains("tool=_pickaxe"), "Ремонт шахты требует кирку: " + MINE);
        assertTrue(MINE.modifiers().contains("carry=cobblestone"), "Ремонт шахты требует запас булыжника: " + MINE);
        assertFalse(HOUSE.modifiers().contains("tool=_pickaxe"), "Ремонт дома не требует кирку: " + HOUSE);
    }

    @Test
    void allows_MineRepairWithoutPickaxe_False() {
        assertFalse(
                ToolGate.allows(MINE, List.of("minecraft:wooden_shovel")),
                "Дух без кирки не получает ремонт шахты: иначе провал «нужна кирка для cobblestone»");
        assertTrue(
                ToolGate.allows(MINE, List.of("minecraft:stone_pickaxe")),
                "С киркой ремонт шахты выдаётся: инструмент есть");
    }

    @Test
    void carries_MineRepairWithoutCobblestone_False() {
        assertFalse(
                ToolGate.carries(MINE, List.of("minecraft:stone_pickaxe", "minecraft:dirt")),
                "Дух без булыжника не получает ремонт шахты: стенку не поставить, проход затопит");
        assertTrue(
                ToolGate.carries(MINE, List.of("minecraft:cobblestone")),
                "С булыжником ремонт шахты выдаётся: стенку есть чем поставить");
        assertTrue(ToolGate.carries(HOUSE, List.of()), "Обычному ремонту запас в руках не нужен");
    }
}
