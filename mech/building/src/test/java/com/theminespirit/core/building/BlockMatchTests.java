package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BlockMatchTests {
    @Test
    void matches_SaplingAgainstTree_Accepted() {
        assertTrue(
                BlockMatch.matches("minecraft:oak_log", "minecraft:oak_sapling"),
                "выросшее дерево на месте саженца — та же посадка: владелец считает саженец и дерево равными");
        assertTrue(
                BlockMatch.matches("minecraft:oak_leaves", "minecraft:oak_sapling"),
                "листва выросшего дерева тоже закрывает клетку саженца");
    }

    @Test
    void matches_TreeOfOtherSpecies_Rejected() {
        assertFalse(
                BlockMatch.matches("minecraft:birch_log", "minecraft:oak_sapling"),
                "берёза не заменяет дубовый саженец: порода входит в план сада");
    }

    @Test
    void matches_WallTorchAgainstStanding_Accepted() {
        assertTrue(
                BlockMatch.matches("minecraft:wall_torch", "minecraft:torch"),
                "настенный факел — тот же факел: владелец разрешил не различать способ крепления");
        assertTrue(
                BlockMatch.matches("minecraft:torch[facing=east]", "minecraft:wall_torch"),
                "стоящий факел закрывает клетку настенного: способ крепления не входит в готовность");
    }

    @Test
    void matches_DoorGateAndChest_IgnoreState() {
        assertTrue(
                BlockMatch.matches("minecraft:oak_door[half=lower,open=true]", "minecraft:oak_door"),
                "открытая дверь не повреждает постройку: состояние open не входит в готовность");
        assertTrue(
                BlockMatch.matches("minecraft:oak_fence_gate[open=false]", "minecraft:oak_fence_gate"),
                "закрытая калитка не повреждает постройку: состояние open не входит в готовность");
        assertTrue(
                BlockMatch.matches("minecraft:chest[facing=east,type=right]", "minecraft:chest"),
                "направление и двойная половина сундука не влияют на готовность склада");
    }
}
