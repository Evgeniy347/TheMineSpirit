package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.farming.api.CropKind;
import org.junit.jupiter.api.Test;

class FarmJobSaplingTests {
    @Test
    void bonemealSaplings_AnyKind_OnlyBoneMeal() {
        assertTrue(
                FarmJob.BONE_MEAL.bonemealSaplings(CropKind.ANY),
                "Костная мука «любого вида» растит и саженцы: без этого посаженное дерево не ускорить");
        assertFalse(
                FarmJob.BONE_MEAL.bonemealSaplings(CropKind.WHEAT),
                "Названная культура растит только её: саженцы в чужую подкормку не попадают");
        assertFalse(
                FarmJob.HARVEST.bonemealSaplings(CropKind.ANY),
                "Саженцы — только забота костной муки, сбор урожая их не трогает");
    }
}
