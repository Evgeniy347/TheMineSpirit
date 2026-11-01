package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class MineStepStandsFallbackTests {
    private static final int FLOOR = 64;
    private static final BlockCoord FROM = new BlockCoord(20, FLOOR + 1, 0);
    private static final MineColumn ENTRANCE = new MineColumn(new BlockCoord(0, FLOOR, 0), 3);
    private static final SafetyView WALL_AND_FLOOR = coord -> {
        if (coord.y() <= FLOOR) {
            return Cell.SOLID;
        }
        if (coord.x() >= 10 && coord.y() <= FLOOR + 6) {
            return Cell.SOLID;
        }
        return Cell.PASSABLE;
    };

    @Test
    void of_SpiritBehindWallButEntranceStandable_PicksVisibleStandForWalk() {
        MineStepStands.Choice picked = MineStepStands.of(ENTRANCE, null, FROM, WALL_AND_FLOOR);

        assertTrue(
                picked.found(),
                "Дух за стеной в 20 блоках от входа: без достижимой стоянки шаг обязан предложить видимую "
                        + "стоянку у ступени, чтобы подход дошёл до неё, а не падал «вход завален»: "
                        + picked.reason());
        assertTrue(
                picked.stands().stream()
                        .anyMatch(stand ->
                                stand.y() == FLOOR + 1 && Math.abs(stand.x()) <= 1 && Math.abs(stand.z()) <= 1),
                "Выбранная стоянка — рядом со ступенью входа (0,65,0), а не за стеной: " + picked.stands());
    }
}
