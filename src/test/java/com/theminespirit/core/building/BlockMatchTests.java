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
    }
}
