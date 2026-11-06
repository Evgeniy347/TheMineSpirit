package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MakeWayPolicyTests {
    @Test
    void mustMove_BusyBuilder_StillMoves() {
        assertTrue(
                MakeWayPolicy.mustMove(true, "build_schematic"),
                "Дух с RUNNING-задачей в клетке будущего блока отходит: иначе стройка ставит забор внутрь него");
    }

    @Test
    void mustMove_AlreadyGoingAway_DoesNotRestart() {
        assertFalse(
                MakeWayPolicy.mustMove(true, "goto"),
                "Дух уже идёт на освобождённую стоянку: повторный goto каждый тик не дал бы ему сдвинуться");
    }

    @Test
    void mustMove_Idle_StillMoves() {
        assertTrue(MakeWayPolicy.mustMove(false, "goto"), "Простаивающий дух в клетке блока тоже отходит");
    }
}
