package com.theminespirit.neoforge.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProtectionGateTests {
    @Test
    void harvest_SpiritBuiltPlanCell_IsProtected() {
        assertTrue(
                ProtectionGate.harvestPlanCell(true, true),
                "Дух не добывает блок, который сам поставил по плану: иначе добыча выедает облицовку шахты, "
                        + "аудит видит air вместо cobblestone, и прогресс падает 75→74%");
    }

    @Test
    void harvest_NaturalCellOrOutsidePlan_IsAllowed() {
        assertFalse(
                ProtectionGate.harvestPlanCell(false, true),
                "Природный блок в клетке плана ещё не поставлен духом: его добывают штатно");
        assertFalse(
                ProtectionGate.harvestPlanCell(true, false),
                "Блок духа вне плана — временный след, его убирает не добыча, а уборка");
    }
}
