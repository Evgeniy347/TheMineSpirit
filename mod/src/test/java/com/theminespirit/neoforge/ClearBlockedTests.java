package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClearBlockedTests {
    @Test
    void plannedFence_FenceOnPlanStrip_IsSkipped() {
        assertTrue(
                ClearBlocked.plannedFence("minecraft:oak_fence", true),
                "Забор на полосе плана ограды уборка не трогает: иначе она снимает уже построенную линию");
    }

    @Test
    void plannedFence_GateOnPlanStrip_IsSkipped() {
        assertTrue(
                ClearBlocked.plannedFence("minecraft:oak_fence_gate", true),
                "Калитка — часть той же оградной линии: по отдельности её не вынимают");
    }

    @Test
    void plannedFence_FenceOffPlanStrip_IsKept() {
        assertFalse(
                ClearBlocked.plannedFence("minecraft:oak_fence", false),
                "Забор вне полосы ограды не защищён планом: его уборка по-прежнему может снять");
    }

    @Test
    void plannedFence_NotFenceOnPlanStrip_IsKept() {
        assertFalse(
                ClearBlocked.plannedFence("minecraft:oak_door", true),
                "Дверь на полосе ограды — не забор: исключение относится только к заборам и калиткам");
    }
}
