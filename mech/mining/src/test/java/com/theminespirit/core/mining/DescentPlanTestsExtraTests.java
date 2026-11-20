package com.theminespirit.core.mining;

import static com.theminespirit.core.mining.DescentPlanTests.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.api.CatalogLimits;
import org.junit.jupiter.api.Test;

class DescentPlanTestsExtraTests {

    @Test
    void from_TorchIntervalLessThanOne_Throws() {
        CellWorld world = new CellWorld();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DescentPlan.from(world, ENTRY, EAST, 1, 0),
                "План с интервалом факелов 0 обязан отклоняться до чтения мира"
                        + " Иначе тест from_TorchIntervalLessThanOne_Throws перестанет ловить эту поломку. При смене from_TorchIntervalLessThanOne_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("Интервал факелов"),
                "Нулевой интервал обязан отклоняться с указанием на интервал факелов"
                        + " Иначе тест from_TorchIntervalLessThanOne_Throws перестанет ловить эту поломку. При смене from_TorchIntervalLessThanOne_Throws проверь это утверждение.");
    }

    @Test
    void from_TargetYAboveWorldCeiling_Throws() {
        CellWorld world = new CellWorld();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DescentPlan.from(world, ENTRY, EAST, 320, 4),
                "План с целью выше потолка мира обязан отклоняться до чтения мира"
                        + " Иначе тест from_TargetYAboveWorldCeiling_Throws перестанет ловить эту поломку. При смене from_TargetYAboveWorldCeiling_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("Целевой этаж"),
                "Цель выше потолка мира обязана отклоняться с указанием на целевой этаж"
                        + " Иначе тест from_TargetYAboveWorldCeiling_Throws перестанет ловить эту поломку. При смене from_TargetYAboveWorldCeiling_Throws проверь это утверждение.");
    }

    @Test
    void from_TargetYBelowWorldFloor_Throws() {
        CellWorld world = new CellWorld();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DescentPlan.from(world, ENTRY, EAST, -65, 4),
                "План с целью под дном мира обязан отклоняться до чтения мира"
                        + " Иначе тест from_TargetYBelowWorldFloor_Throws перестанет ловить эту поломку. При смене from_TargetYBelowWorldFloor_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("Целевой этаж"),
                "Цель под дном мира обязана отклоняться с указанием на целевой этаж"
                        + " Иначе тест from_TargetYBelowWorldFloor_Throws перестанет ловить эту поломку. При смене from_TargetYBelowWorldFloor_Throws проверь это утверждение.");
    }

    @Test
    void from_EntryOutsideWorldHeight_Throws() {
        CellWorld world = new CellWorld();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DescentPlan.from(world, new BlockCoord(0, 320, 0), EAST, 1, 4),
                "План от точки входа вне мира обязан отклоняться до чтения мира"
                        + " Иначе тест from_EntryOutsideWorldHeight_Throws перестанет ловить эту поломку. При смене from_EntryOutsideWorldHeight_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("вне мира"),
                "Вход вне мира по высоте обязан отклоняться до любого чтения мира"
                        + " Иначе тест from_EntryOutsideWorldHeight_Throws перестанет ловить эту поломку. При смене from_EntryOutsideWorldHeight_Throws проверь это утверждение.");
    }

    @Test
    void worldBounds_PlanConstants_MatchCatalogLimits() {
        assertEquals(
                CatalogLimits.WORLD_Y_MIN,
                DescentPlan.WORLD_MIN_Y,
                "Граница мира в плане спуска обязана совпадать с каталогом: расхождение пустит план за дно мира"
                        + " Иначе тест worldBounds_PlanConstants_MatchCatalogLimits перестанет ловить эту поломку. При смене worldBounds_PlanConstants_MatchCatalogLimits проверь это утверждение.");
        assertEquals(
                CatalogLimits.WORLD_Y_MAX,
                DescentPlan.WORLD_MAX_Y,
                "Граница мира в плане спуска обязана совпадать с каталогом: расхождение пустит план выше потолка"
                        + " Иначе тест worldBounds_PlanConstants_MatchCatalogLimits перестанет ловить эту поломку. При смене worldBounds_PlanConstants_MatchCatalogLimits проверь это утверждение.");
    }
}
