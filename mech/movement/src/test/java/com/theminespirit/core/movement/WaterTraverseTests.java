package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class WaterTraverseTests {
    @Test
    void phase_FromBankIntoWater_Enters() {
        assertEquals(
                WaterTraverse.Phase.ENTER,
                WaterTraverse.phase(false, true),
                "С берега в воду дух обязан сходить в воду, а не идти по поверхности: иначе SWIM_STALLED у кромки");
    }

    @Test
    void phase_InWaterTowardsWater_Swims() {
        assertEquals(
                WaterTraverse.Phase.SWIM,
                WaterTraverse.phase(true, true),
                "В воде следующий водный шаг — плавание по маршруту, без выхода на берег");
    }

    @Test
    void phase_FromWaterOntoLand_Exits() {
        assertEquals(
                WaterTraverse.Phase.EXIT,
                WaterTraverse.phase(true, false),
                "Последний водный шаг обязан выводить на берег, а не оставлять духа плавать у цели");
    }

    @Test
    void arrived_CellCentre_IsArrived() {
        assertTrue(
                WaterTraverse.arrived(1.5, 63.0, 2.5, new BlockCoord(1, 63, 2)),
                "Дух в центре водной клетки уже доплыл: повторный запрос движения держит его на месте");
    }

    @Test
    void arrived_CellBeyondTolerance_IsNotArrived() {
        assertFalse(
                WaterTraverse.arrived(2.2, 63.0, 2.5, new BlockCoord(1, 63, 2)),
                "Дух в соседней клетке ещё не доплыл: шаг нельзя закрывать по одной высоте");
    }
}
