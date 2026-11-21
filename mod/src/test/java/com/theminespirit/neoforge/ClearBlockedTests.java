package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClearBlockedTests {
    @Test
    void plannedFence_FenceOnPlanStrip_IsSkipped() {
        assertTrue(
                ClearBlocked.plannedFence("minecraft:oak_fence", true),
                "Забор на полосе плана ограды уборка не трогает: иначе она снимает уже построенную линию"
                        + " При смене plannedFence_FenceOnPlanStrip_IsSkipped проверь это утверждение.");
    }

    @Test
    void plannedFence_GateOnPlanStrip_IsSkipped() {
        assertTrue(
                ClearBlocked.plannedFence("minecraft:oak_fence_gate", true),
                "Калитка — часть той же оградной линии: по отдельности её не вынимают"
                        + " Иначе тест plannedFence_GateOnPlanStrip_IsSkipped перестанет ловить эту поломку. При смене plannedFence_GateOnPlanStrip_IsSkipped проверь это утверждение.");
    }

    @Test
    void plannedFence_FenceOffPlanStrip_IsKept() {
        assertFalse(
                ClearBlocked.plannedFence("minecraft:oak_fence", false),
                "Забор вне полосы ограды не защищён планом: его уборка по-прежнему может снять"
                        + " Иначе тест plannedFence_FenceOffPlanStrip_IsKept перестанет ловить эту поломку. При смене plannedFence_FenceOffPlanStrip_IsKept проверь это утверждение.");
    }

    @Test
    void plannedFence_NotFenceOnPlanStrip_IsKept() {
        assertFalse(
                ClearBlocked.plannedFence("minecraft:oak_door", true),
                "Дверь на полосе ограды — не забор: исключение относится только к заборам и калиткам"
                        + " Иначе тест plannedFence_NotFenceOnPlanStrip_IsKept перестанет ловить эту поломку. При смене plannedFence_NotFenceOnPlanStrip_IsKept проверь это утверждение.");
    }
}
