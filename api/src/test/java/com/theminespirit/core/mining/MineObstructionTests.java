package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.api.MineObstruction;
import com.theminespirit.core.observation.BlockCoord;
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

    @Test
    void underWork_SameBlocker_KeepsClearing() {
        BlockCoord blocker = new BlockCoord(-164, 67, -515);

        assertTrue(
                MineObstruction.underWork(blocker, blocker),
                "повторный запрос той же помехи — ломка уже идёт: проходка ждёт, а не падает с отказом стоянки");
    }

    @Test
    void underWork_OtherOrNoBlocker_StartsNewClearing() {
        assertFalse(
                MineObstruction.underWork(new BlockCoord(-164, 67, -515), new BlockCoord(-164, 62, -512))
                        || MineObstruction.underWork(null, new BlockCoord(-164, 67, -515)),
                "новая помеха или её отсутствие — повод начать ломку заново, а не считать её уже начатой");
    }
}
