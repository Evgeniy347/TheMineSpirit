package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class MineDescentBottomTests {

    @Test
    void standOf_SurfaceHome_BottomStepAtDiamondDepthAwayFromHome() {
        BlockCoord home = new BlockCoord(0, 64, 0);

        BlockCoord stand = MineDescentBottom.standOf(home).orElseThrow();

        assertEquals(
                51,
                stand.y(),
                "Низ спуска — над полом нижней ступени y=50 (схема mine_descent_v1): уровни шахты 2–3 "
                        + "расширяются оттуда, а не у дома");
        int dx = stand.x() - home.x();
        int dz = stand.z() - home.z();
        assertTrue(
                dx * dx + dz * dz > 100,
                "Низ спуска далеко от дома (" + stand + "): выработка у дома на поверхности сносила верстак");
    }
}
