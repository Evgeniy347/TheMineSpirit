package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class MineDescentBottomTests {

    @Test
    void standOf_SurfaceHome_BottomStepAtYMinus58AwayFromHome() {
        BlockCoord home = new BlockCoord(0, 64, 0);

        BlockCoord stand = MineDescentBottom.standOf(home).orElseThrow();

        assertEquals(
                -57,
                stand.y(),
                "Низ спуска — над полом нижней ступени y=−58, где больше всего алмазов (схема mine_descent_v1): уровни шахты 2–3 "
                        + "расширяются оттуда, а не у дома");
        int dx = stand.x() - home.x();
        int dz = stand.z() - home.z();
        assertTrue(
                dx * dx + dz * dz > 100,
                "Низ спуска далеко от дома (" + stand + "): выработка у дома на поверхности сносила верстак");
    }

    @Test
    void standOf_EntranceBelowTarget_ZeroStepsAtEntrance() {
        BlockCoord entrance = new BlockCoord(0, -60, 0);

        BlockCoord stand = MineDescentBottom.standOf(entrance).orElseThrow();

        assertEquals(
                -57,
                stand.y(),
                "Вход уже ниже цели спуска (мир GameTest у дна): 0 ступеней, низ спуска — вход, а не исключение "
                        + "«повтор требует 111 сегментов»");
    }
}
