package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnchorSpotTests {
    private static final BlockCoord FROM = new BlockCoord(3, 2, 0);

    @Test
    void near_AnchorTwoAboveFloor_SnapsToStandBelow() {
        CellWorld world = new CellWorld().set(0, 0, 0, Cell.SOLID);

        Optional<BlockCoord> spot = AnchorSpot.near(new BlockCoord(0, 2, 0), FROM, world);

        assertEquals(
                Optional.of(new BlockCoord(0, 1, 0)),
                spot,
                "Якорь в воздухе на 1–2 блока выше пола обязан замениться стоянкой на полу: иначе дух "
                        + "бьётся в таймаут возврата каждые ~90 секунд (аномалия 7)");
    }

    @Test
    void near_AnchorIsStand_KeepsAnchor() {
        CellWorld world = new CellWorld().set(0, 0, 0, Cell.SOLID);

        Optional<BlockCoord> spot = AnchorSpot.near(new BlockCoord(0, 1, 0), FROM, world);

        assertEquals(
                Optional.of(new BlockCoord(0, 1, 0)),
                spot,
                "Якорь, на котором дух уже может стоять, не смещается: иначе возврат уводит его с места тревоги");
    }

    @Test
    void near_OnlyHighStand_IsRejected() {
        CellWorld world = new CellWorld()
                .set(0, 9, 0, Cell.SOLID)
                .set(1, 9, 0, Cell.PASSABLE)
                .set(1, 8, 0, Cell.SOLID);

        Optional<BlockCoord> spot = AnchorSpot.near(new BlockCoord(0, 9, 0), new BlockCoord(0, 0, 0), world);

        assertEquals(
                Optional.empty(),
                spot,
                "Стоянка на 9 блоков выше духа не годится для якоря: возврат к ней снова упрётся в таймаут, "
                        + "а не в достижимую стоянку");
    }
}
