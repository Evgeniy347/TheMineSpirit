package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class ClearScaffoldReachTests {
    private static final BlockCoord TOP = new BlockCoord(-175, 97, -517);

    @Test
    void decide_HighCellFarAway_ApproachFirstNoPillar() {
        ClearScaffold.Decision decision = ClearScaffold.decide(new BlockCoord(-128, 68, -485), TOP, 3, false, 2);

        assertEquals(
                ClearScaffold.Action.WORK,
                decision.action(),
                "Высокая клетка в 47 блоках — сначала подойти к её колонке; столб на месте не строится (сервер 12.11: "
                        + "дух поднял 26 блоков земли там, где стоял)");
    }

    @Test
    void decide_HighCellBeside_Climbs() {
        ClearScaffold.Decision decision = ClearScaffold.decide(new BlockCoord(-176, 68, -517), TOP, 3, false, 2);

        assertEquals(ClearScaffold.Action.CLIMB, decision.action(), "Колонка рядом — подъём к верхней клетке");
        assertEquals(29, decision.height(), "Подъём от ног до верхней клетки");
    }
}
