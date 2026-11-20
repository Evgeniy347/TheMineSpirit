package com.theminespirit.core.mining;

import static com.theminespirit.core.mining.SafeMinePlanTests.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class SafeMinePlanTestsExtraTests {

    @Test
    void tunnel_TorchIntervalOutOfRange_Throws() {
        CellWorld world = new CellWorld();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SafeMinePlan.tunnel(world, new BlockCoord(0, 10, 0), EAST, 4, 0),
                "Интервал факелов 0 обязан отклоняться до чтения мира"
                        + " Иначе тест tunnel_TorchIntervalOutOfRange_Throws перестанет ловить эту поломку. При смене tunnel_TorchIntervalOutOfRange_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("Интервал факелов"),
                "Отказ обязан называть интервал факелов"
                        + " Иначе тест tunnel_TorchIntervalOutOfRange_Throws перестанет ловить эту поломку. При смене tunnel_TorchIntervalOutOfRange_Throws проверь это утверждение.");
    }

    @Test
    void staircase_TargetYAboveWorldCeiling_Throws() {
        CellWorld world = new CellWorld();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SafeMinePlan.staircase(world, new BlockCoord(0, 10, 0), EAST, 320, 4),
                "Цель выше потолка мира обязана отклоняться до чтения мира"
                        + " Иначе тест staircase_TargetYAboveWorldCeiling_Throws перестанет ловить эту поломку. При смене staircase_TargetYAboveWorldCeiling_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("Целевой этаж"),
                "Отказ обязан называть целевой этаж"
                        + " Иначе тест staircase_TargetYAboveWorldCeiling_Throws перестанет ловить эту поломку. При смене staircase_TargetYAboveWorldCeiling_Throws проверь это утверждение.");
    }

    @Test
    void worldBounds_ComparedToDescentPlan_AreEqual() {
        assertEquals(
                DescentPlan.WORLD_MIN_Y,
                SafeMinePlan.WORLD_MIN_Y,
                "Граница мира в плане шахты обязана совпадать с планом спуска: расхождение пустит шахту за дно"
                        + " Иначе тест worldBounds_ComparedToDescentPlan_AreEqual перестанет ловить эту поломку. При смене worldBounds_ComparedToDescentPlan_AreEqual проверь это утверждение.");
        assertEquals(
                DescentPlan.WORLD_MAX_Y,
                SafeMinePlan.WORLD_MAX_Y,
                "Граница мира в плане шахты обязана совпадать с планом спуска: расхождение пустит шахту выше потолка"
                        + " Иначе тест worldBounds_ComparedToDescentPlan_AreEqual перестанет ловить эту поломку. При смене worldBounds_ComparedToDescentPlan_AreEqual проверь это утверждение.");
    }
}
