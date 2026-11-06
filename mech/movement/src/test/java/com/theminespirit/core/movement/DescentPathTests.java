package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class DescentPathTests {
    @Test
    void descent_OneHundredTwentyBlocks_Refused() {
        List<BlockCoord> path = List.of(new BlockCoord(0, 70, 0), new BlockCoord(0, 60, 0), new BlockCoord(0, -50, 0));

        String refusal = DescentPath.refusalOrNull(path, false);

        assertTrue(
                refusal != null && refusal.contains("120"),
                "Путь вниз на 120 блоков отвергается: помощник не уходит за духом в бездну (observe24 B3): " + refusal);
    }

    @Test
    void descent_FortyTwoBlocks_Refused() {
        List<BlockCoord> path = List.of(new BlockCoord(0, 70, 0), new BlockCoord(0, 47, 0));

        assertTrue(
                DescentPath.refusalOrNull(path, false) != null,
                "Спуск на 23 блока тоже «десятки блоков»: предел " + DescentPath.MAX_DESCENT);
    }

    @Test
    void descent_ShortStep_Allowed() {
        List<BlockCoord> path = List.of(new BlockCoord(0, 70, 0), new BlockCoord(0, 69, 0));

        assertNull(
                DescentPath.refusalOrNull(path, false),
                "Короткий спуск на блок к цели не запрещён: дух ходит по рельефу");
    }

    @Test
    void descent_EndsInWater_Refused() {
        List<BlockCoord> path = List.of(new BlockCoord(0, 70, 0), new BlockCoord(0, 69, 0));

        String refusal = DescentPath.refusalOrNull(path, true);

        assertTrue(
                refusal != null && refusal.contains("воду"),
                "Путь, упирающийся в воду, не выход: дух захлёбывается вместо спасения: " + refusal);
    }
}
