package com.theminespirit.core.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RepairCalculatorTests {
    private static final int PICKAXE_MAX = 250;

    @Test
    void grindstoneResult_TwoDamagedItems_AddsFivePercentOfMax() {
        int result = RepairCalculator.grindstoneResult(50, 60, PICKAXE_MAX);
        assertEquals(123, result,
                "50 + 60 + 5% от 250 (13) = 123: точило складывает прочность и добавляет бонус");
    }

    @Test
    void grindstoneResult_CombinedAboveMax_IsCappedAtMax() {
        int result = RepairCalculator.grindstoneResult(100, 150, PICKAXE_MAX);
        assertEquals(PICKAXE_MAX, result,
                "100 + 150 + 13 = 263 обрезается до максимальной прочности 250: чинить выше максимума нельзя");
    }

    @Test
    void grindstoneResult_BothBroken_ReturnsBonusOnly() {
        int result = RepairCalculator.grindstoneResult(0, 0, PICKAXE_MAX);
        assertEquals(13, result, "0 + 0 + 5% от 250 (13) = 13: два сломанных предмета дают только бонус точила");
    }

    @Test
    void grindstoneResult_NegativeDurability_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> RepairCalculator.grindstoneResult(-1, 100, PICKAXE_MAX),
                "отрицательная прочность недопустима: сломанный предмет даёт ноль, а не минус");
    }

    @Test
    void grindstoneResult_ZeroMaxDurability_Throws() {
        assertThrows(IllegalArgumentException.class, () -> RepairCalculator.grindstoneResult(100, 100, 0),
                "нулевая максимальная прочность недопустима: у предмета без прочности точило бессмысленно");
    }

    @Test
    void damagedEnoughToRepair_ExactlyAtThreshold_ReturnsTrue() {
        assertTrue(RepairCalculator.damagedEnoughToRepair(225, PICKAXE_MAX, 90),
                "прочность ровно на пороге (90% от 250 = 225) ещё считается достаточной для ремонта");
    }

    @Test
    void damagedEnoughToRepair_AboveThreshold_ReturnsFalse() {
        assertFalse(RepairCalculator.damagedEnoughToRepair(226, PICKAXE_MAX, 90),
                "прочность выше порога (226 > 225) значит почти целый предмет: чинить не нужно");
    }

    @Test
    void damagedEnoughToRepair_FullDurability_ReturnsFalse() {
        assertFalse(RepairCalculator.damagedEnoughToRepair(PICKAXE_MAX, PICKAXE_MAX, 90),
                "предмет с полной прочностью порог отвергает: чинить нечего");
    }

    @Test
    void chooseMethod_TwoDamagedPlainItems_ReturnsGrindstone() {
        assertEquals(RepairMethod.GRINDSTONE, RepairCalculator.chooseMethod(2, false, 100, PICKAXE_MAX, 90),
                "два одинаковых повреждённых незачарованных предмета чинятся в точиле");
    }

    @Test
    void chooseMethod_EnchantedPair_ReturnsNone() {
        assertEquals(RepairMethod.NONE, RepairCalculator.chooseMethod(2, true, 100, PICKAXE_MAX, 90),
                "зачарованную пару в точило не кладут: чары потеряются, а наковальни с запасом опыта у духа нет");
    }

    @Test
    void chooseMethod_SingleItem_ReturnsNone() {
        assertEquals(RepairMethod.NONE, RepairCalculator.chooseMethod(1, false, 100, PICKAXE_MAX, 90),
                "для точила нужны два одинаковых предмета: одно повреждённое не починить");
    }

    @Test
    void chooseMethod_ItemAboveThreshold_ReturnsNone() {
        assertEquals(RepairMethod.NONE, RepairCalculator.chooseMethod(2, false, 240, PICKAXE_MAX, 90),
                "если лучший из пары ещё крепок (240 выше порога 225), второй предмет тратить незачем");
    }
}
