package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
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

    @Test
    void pillarSpot_StandingOnWalkway_MovesBesideIt() {
        BlockCoord feet = new BlockCoord(-176, 68, -517);
        Optional<BlockCoord> spot = ClearScaffold.pillarSpot(feet, TOP, cell -> cell.x() == -176, cell -> true, 2);

        assertEquals(
                Optional.of(new BlockCoord(-177, 68, -517)),
                spot,
                "Дух на тропе плана — столб ставится рядом, не на проходе: стоянка на тропе дороже");
    }

    @Test
    void pillarSpot_OffWalkwayOrNoSpot_StaysPut() {
        BlockCoord feet = new BlockCoord(-176, 68, -517);

        assertEquals(
                Optional.empty(),
                ClearScaffold.pillarSpot(feet, TOP, cell -> false, cell -> true, 2),
                "Дух не на тропе — столб там, где стоит");
        assertEquals(
                Optional.empty(),
                ClearScaffold.pillarSpot(feet, TOP, cell -> true, cell -> true, 2),
                "Вокруг одна тропа — столб на тропе допускается (дороже, но иначе цель не достать)");
    }
}
