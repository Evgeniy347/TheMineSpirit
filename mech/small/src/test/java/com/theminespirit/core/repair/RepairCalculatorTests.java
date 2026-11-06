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
        assertEquals(123, result, "50 + 60 + 5% от 250 (13) = 123: точило складывает прочность и добавляет бонус");
    }

    @Test
    void grindstoneResult_CombinedAboveMax_IsCappedAtMax() {
        int result = RepairCalculator.grindstoneResult(100, 150, PICKAXE_MAX);
        assertEquals(
                PICKAXE_MAX,
                result,
                "100 + 150 + 13 = 263 обрезается до максимальной прочности 250: чинить выше максимума нельзя");
    }

    @Test
    void grindstoneResult_BothBroken_ReturnsBonusOnly() {
        int result = RepairCalculator.grindstoneResult(0, 0, PICKAXE_MAX);
        assertEquals(13, result, "0 + 0 + 5% от 250 (13) = 13: два сломанных предмета дают только бонус точила");
    }

    @Test
    void grindstoneResult_NegativeDurability_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RepairCalculator.grindstoneResult(-1, 100, PICKAXE_MAX),
                "отрицательная прочность недопустима: сломанный предмет даёт ноль, а не минус");
    }

    @Test
    void grindstoneResult_ZeroMaxDurability_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RepairCalculator.grindstoneResult(100, 100, 0),
                "нулевая максимальная прочность недопустима: у предмета без прочности точило бессмысленно");
    }

    @Test
    void damagedEnoughToRepair_ExactlyAtThreshold_ReturnsTrue() {
        assertTrue(
                RepairCalculator.damagedEnoughToRepair(225, PICKAXE_MAX, 90),
                "прочность ровно на пороге (90% от 250 = 225) ещё считается достаточной для ремонта");
    }

    @Test
    void damagedEnoughToRepair_AboveThreshold_ReturnsFalse() {
        assertFalse(
                RepairCalculator.damagedEnoughToRepair(226, PICKAXE_MAX, 90),
                "прочность выше порога (226 > 225) значит почти целый предмет: чинить не нужно");
    }

    @Test
    void damagedEnoughToRepair_FullDurability_ReturnsFalse() {
        assertFalse(
                RepairCalculator.damagedEnoughToRepair(PICKAXE_MAX, PICKAXE_MAX, 90),
                "предмет с полной прочностью порог отвергает: чинить нечего");
    }

    @Test
    void anvilResult_WornTool_AddsQuarterOfMax() {
        assertEquals(
                212,
                RepairCalculator.anvilResult(150, 250, 25),
                "150 + 25% от 250 (62) = 212: наковальня добавляет четверть максимума за материал");
    }

    @Test
    void anvilResult_NearFull_CapsAtMax() {
        assertEquals(
                250,
                RepairCalculator.anvilResult(230, 250, 25),
                "230 + 62 = 292 обрезается до максимума 250: выше предела прочности не бывает");
    }

    @Test
    void anvilResult_BrokenTool_RestoresOnlyQuarter() {
        assertEquals(
                62,
                RepairCalculator.anvilResult(0, 250, 25),
                "0 + 62 = 62: у сломанного инструмента материал восстанавливает только четверть максимума");
    }

    @Test
    void anvilResult_PercentOutOfRange_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RepairCalculator.anvilResult(100, 250, 0),
                "Нулевая доля ремонта недопустима: материал тратился бы впустую");
        assertThrows(
                IllegalArgumentException.class,
                () -> RepairCalculator.anvilResult(100, 250, 101),
                "Доля ремонта выше 100% недопустима: предел прочности переполнился бы");
    }

    @Test
    void chooseMethod_MaterialOnly_ReturnsAnvil() {
        assertEquals(
                RepairMethod.ANVIL,
                RepairCalculator.chooseMethod(
                        RepairCosts.defaults(), new RepairCalculator.Availability(true, false, false, true)),
                "Есть материал и нет второго инструмента — чиним наковальней");
    }

    @Test
    void chooseMethod_SecondToolOnly_ReturnsGrindstone() {
        assertEquals(
                RepairMethod.GRINDSTONE,
                RepairCalculator.chooseMethod(
                        RepairCosts.defaults(), new RepairCalculator.Availability(false, true, false, true)),
                "Нет материала, но есть второй такой же инструмент — чиним точилом");
    }

    @Test
    void chooseMethod_BothAvailable_CheapestWins() {
        assertEquals(
                RepairMethod.ANVIL,
                RepairCalculator.chooseMethod(
                        new RepairCosts(1, 2, 10), new RepairCalculator.Availability(true, true, false, true)),
                "Материал (1) + опыт (1×2) = 3 дешевле второго инструмента (10): выбираем наковальню");
        assertEquals(
                RepairMethod.GRINDSTONE,
                RepairCalculator.chooseMethod(
                        new RepairCosts(1, 20, 10), new RepairCalculator.Availability(true, true, false, true)),
                "Материал (1) + опыт (1×20) = 21 дороже второго инструмента (10): выбираем точило");
    }

    @Test
    void chooseMethod_Enchanted_RequiresAnvil() {
        assertEquals(
                RepairMethod.ANVIL,
                RepairCalculator.chooseMethod(
                        RepairCosts.defaults(), new RepairCalculator.Availability(true, true, true, true)),
                "Зачарованный инструмент чинится только наковальней: точило снимет чары");
        assertEquals(
                RepairMethod.NONE,
                RepairCalculator.chooseMethod(
                        RepairCosts.defaults(), new RepairCalculator.Availability(false, true, true, true)),
                "Зачарованный инструмент без материала не чинится вовсе: точило снимет чары, а наковальня без "
                        + "материала бесполезна");
    }

    @Test
    void chooseMethod_NotDamagedEnough_ReturnsNone() {
        assertEquals(
                RepairMethod.NONE,
                RepairCalculator.chooseMethod(
                        RepairCosts.defaults(), new RepairCalculator.Availability(true, true, false, false)),
                "Крепкий инструмент не чиним даже при материале и паре: ресурс тратился бы зря");
    }

    @Test
    void chooseMethod_NothingAvailable_ReturnsNone() {
        assertEquals(
                RepairMethod.NONE,
                RepairCalculator.chooseMethod(
                        RepairCosts.defaults(), new RepairCalculator.Availability(false, false, false, true)),
                "Без материала и без второго инструмента чинить нечем");
    }

    @Test
    void chooseMethod_NullCosts_Throws() {
        assertThrows(
                NullPointerException.class,
                () -> RepairCalculator.chooseMethod(null, new RepairCalculator.Availability(true, true, false, true)),
                "Стоимость без чисел не задать: выбор способа обязан падать, а не подставлять нули");
    }
}
