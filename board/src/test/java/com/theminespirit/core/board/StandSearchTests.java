package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class StandSearchTests {
    private static final BlockCoord WALL = new BlockCoord(-171, 71, -489);

    @Test
    void nearest_CenterIsStandable_KeepsCenter() {
        assertEquals(WALL, StandSearch.nearest(WALL, cell -> true), "свободный центр остаётся домом");
    }

    @Test
    void nearest_CenterInsideWall_ReturnsClosestStand() {
        BlockCoord door = new BlockCoord(-173, 71, -489);
        BlockCoord far = new BlockCoord(-180, 71, -489);

        assertEquals(
                door,
                StandSearch.nearest(WALL, cell -> cell.equals(door) || cell.equals(far)),
                "якорь под стеной склада: дом — ближайшая клетка, где можно стоять, иначе все «иди домой» падают");
    }

    @Test
    void nearest_NoStandNearby_KeepsCenter() {
        assertEquals(
                WALL, StandSearch.nearest(WALL, cell -> false), "стоянки нет: центр без подмены, ошибку покажет путь");
    }
}
