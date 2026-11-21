package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.planner.api.FuelTable;
import org.junit.jupiter.api.Test;

class FuelTableCookTicksTests {
    @Test
    void fuelNeeded_RecipeCookTime_ReplacesDefaultTwoHundred() {
        assertEquals(
                1,
                FuelTable.fuelNeeded(16, 1600, 100),
                "16 предметов по 100 тиков — 1600 тиков, один уголь: расход считается по времени рецепта"
                        + " Иначе тест fuelNeeded_RecipeCookTime_ReplacesDefaultTwoHundred перестанет ловить эту поломку. При смене fuelNeeded_RecipeCookTime_ReplacesDefaultTwoHundred проверь это утверждение.");
        assertEquals(
                2,
                FuelTable.fuelNeeded(17, 1600, 100),
                "17 предметов по 100 тиков — 1700 тиков, два угля: недобор остановит печь"
                        + " Иначе тест fuelNeeded_RecipeCookTime_ReplacesDefaultTwoHundred перестанет ловить эту поломку. При смене fuelNeeded_RecipeCookTime_ReplacesDefaultTwoHundred проверь это утверждение.");
    }

    @Test
    void fuelNeeded_TwoArguments_UsesDefaultFurnaceCookTime() {
        assertEquals(
                FuelTable.fuelNeeded(8, 300, FuelTable.COOK_TICKS),
                FuelTable.fuelNeeded(8, 300),
                "Короткая форма обязана считать по 200 тиков обычной печи, как и раньше: на ней стоит резолвер"
                        + " Иначе тест fuelNeeded_TwoArguments_UsesDefaultFurnaceCookTime перестанет ловить эту поломку. При смене fuelNeeded_TwoArguments_UsesDefaultFurnaceCookTime проверь это утверждение.");
    }

    @Test
    void fuelNeeded_ZeroCookTicks_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FuelTable.fuelNeeded(3, 1600, 0),
                "Нулевое время плавки обязано отвергаться: расход топлива на мгновенную плавку — ноль и мёртвый шаг"
                        + " Иначе тест fuelNeeded_ZeroCookTicks_IsRejected перестанет ловить эту поломку. При смене fuelNeeded_ZeroCookTicks_IsRejected проверь это утверждение.");
    }
}
