package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class MineDescentBottomTests {

    @Test
    void standOf_SurfaceHome_BottomStepTwentyBelowEntryAwayFromHome() {
        BlockCoord home = new BlockCoord(0, 64, 0);

        BlockCoord stand = MineDescentBottom.standOf(home).orElseThrow();

        assertEquals(
                45,
                stand.y(),
                "Низ спуска — над полом 20-й ступени от входа y=63 (схема mine_descent_v1): уровни шахты 2–3 "
                        + "расширяются оттуда, а не у дома");
        int dx = stand.x() - home.x();
        int dz = stand.z() - home.z();
        assertTrue(
                dx * dx + dz * dz > 100,
                "Низ спуска далеко от дома (" + stand + "): выработка у дома на поверхности сносила верстак");
    }

    @Test
    void standOf_GameTestDepthHome_DepthFromEntry() {
        BlockCoord home = new BlockCoord(0, -40, 0);

        BlockCoord stand = MineDescentBottom.standOf(home).orElseThrow();

        assertEquals(-40 - 1 - 19 + 1, stand.y(), "Глубина спуска — от входа, а не до мирового y=50 (выше дома)");
    }
}
