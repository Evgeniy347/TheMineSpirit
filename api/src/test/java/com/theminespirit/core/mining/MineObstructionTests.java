package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MineObstructionTests {
    @Test
    void clearable_CommonStoneAndDirt_Allowed() {
        assertTrue(
                MineObstruction.clearable("minecraft:stone") && MineObstruction.clearable("minecraft:dirt"),
                "камень и земля, перекрывающие лестницу спуска, выламывают: иначе пройти вниз нельзя");
    }

    @Test
    void clearable_CoalAndIron_Allowed() {
        assertTrue(
                MineObstruction.clearable("minecraft:coal_ore")
                        && MineObstruction.clearable("minecraft:deepslate_iron_ore"),
                "уголь и железо пока входят в список помех спуска: решение владельца 19.11");
    }

    @Test
    void clearable_OtherOresAndLining_Refused() {
        assertFalse(
                MineObstruction.clearable("minecraft:copper_ore")
                        && MineObstruction.clearable("minecraft:diamond_ore")
                        && MineObstruction.clearable("minecraft:oak_planks")
                        && MineObstruction.clearable("minecraft:chest"),
                "медь, алмаз и всё не каменное/не уголь/не железо помехой спуска не считается: список расширяют "
                        + "осознанно, иначе шахта выломает любую постройку");
    }
}
