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
        assertEquals(
                123,
                result,
                "50 + 60 + 5% от 250 (13) = 123: точило складывает прочность и добавляет бонус, иначе расчёт не "
                        + "совпадает с ванилью. При смене grindstoneResult проверь строку обрезки ниже в тесте");
    }

    @Test
    void grindstoneResult_CombinedAboveMax_IsCappedAtMax() {
        int result = RepairCalculator.grindstoneResult(100, 150, PICKAXE_MAX);
        assertEquals(
                PICKAXE_MAX,
                result,
                "100 + 150 + 13 = 263 обрезается до максимальной прочности 250: чинить выше максимума нельзя, иначе "
                        + "предмет переполняется. При смене обрезки проверь строку бонуса ниже в тесте");
    }

    @Test
    void grindstoneResult_BothBroken_ReturnsBonusOnly() {
        int result = RepairCalculator.grindstoneResult(0, 0, PICKAXE_MAX);
        assertEquals(
                13,
                result,
                "0 + 0 + 5% от 250 (13) = 13: два сломанных предмета дают только бонус точила, иначе ноль даёт "
                        + "прочность. При смене проверки проверь строку отрицательной ниже в тесте");
    }

    @Test
    void grindstoneResult_NegativeDurability_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RepairCalculator.grindstoneResult(-1, 100, PICKAXE_MAX),
                "Отрицательная прочность недопустима: сломанный предмет даёт ноль, а не минус, иначе прочность уходит "
                        + "ниже нуля. При смене проверки проверь строку нулевого максимума ниже в тесте");
    }

    @Test
    void grindstoneResult_ZeroMaxDurability_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RepairCalculator.grindstoneResult(100, 100, 0),
                "Нулевая максимальная прочность недопустима: у предмета без прочности точило бессмысленно, иначе "
                        + "деление на ноль. При смене проверки проверь строку порога ниже в тесте");
    }

    @Test
    void damagedEnoughToRepair_ExactlyAtThreshold_ReturnsTrue() {
        assertTrue(
                RepairCalculator.damagedEnoughToRepair(225, PICKAXE_MAX, 90),
                "Прочность ровно на пороге (90% от 250 = 225) ещё считается достаточной для ремонта, иначе граница "
                        + "отсекает ремонт. При смене damagedEnoughToRepair проверь строку выше порога ниже в тесте");
    }

    @Test
    void damagedEnoughToRepair_AboveThreshold_ReturnsFalse() {
        assertFalse(
                RepairCalculator.damagedEnoughToRepair(226, PICKAXE_MAX, 90),
                "Прочность выше порога (226 > 225) значит почти целый предмет: чинить не нужно, иначе запас тратится "
                        + "зря. При смене сравнения проверь строку полной ниже в тесте");
    }

    @Test
    void damagedEnoughToRepair_FullDurability_ReturnsFalse() {
        assertFalse(
                RepairCalculator.damagedEnoughToRepair(PICKAXE_MAX, PICKAXE_MAX, 90),
                "Предмет с полной прочностью порог отвергает: чинить нечего, иначе ремонт целого. При смене проверки "
                        + "проверь строку наковальни ниже в тесте");
    }

    @Test
    void anvilResult_WornTool_AddsQuarterOfMax() {
        assertEquals(
                212,
                RepairCalculator.anvilResult(150, 250, 25),
                "150 + 25% от 250 (62) = 212: наковальня добавляет четверть максимума за материал, иначе расчёт не "
                        + "совпадает. При смене anvilResult проверь строку обрезки ниже в тесте");
    }

    @Test
    void anvilResult_NearFull_CapsAtMax() {
        assertEquals(
                250,
                RepairCalculator.anvilResult(230, 250, 25),
                "230 + 62 = 292 обрезается до максимума 250: выше предела прочности не бывает, иначе предмет "
                        + "переполняется. При смене обрезки проверь строку сломанного ниже в тесте");
    }

    @Test
    void anvilResult_BrokenTool_RestoresOnlyQuarter() {
        assertEquals(
                62,
                RepairCalculator.anvilResult(0, 250, 25),
                "0 + 62 = 62: у сломанного инструмента материал восстанавливает только четверть максимума, иначе "
                        + "сломанный чинится полностью. При смене anvilResult проверь строку процента ниже в тесте");
    }

    @Test
    void anvilResult_PercentOutOfRange_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RepairCalculator.anvilResult(100, 250, 0),
                "Нулевая доля ремонта недопустима: материал тратился бы впустую, иначе ремонт без эффекта. При смене "
                        + "проверки проверь строку 101 ниже в тесте");
        assertThrows(
                IllegalArgumentException.class,
                () -> RepairCalculator.anvilResult(100, 250, 101),
                "Доля ремонта выше 100% недопустима: предел прочности переполнился бы, иначе обрезка маскирует "
                        + "ошибку. При смене проверки проверь строку material only ниже в тесте");
    }

    @Test
    void chooseMethod_MaterialOnly_ReturnsAnvil() {
        assertEquals(
                RepairMethod.ANVIL,
                RepairCalculator.chooseMethod(
                        RepairCosts.defaults(), new RepairCalculator.Availability(true, false, false, true)),
                "Есть материал и нет второго инструмента — чиним наковальней, иначе ремонт не начинается. При смене "
                        + "chooseMethod проверь строку точила ниже в тесте");
    }

    @Test
    void chooseMethod_SecondToolOnly_ReturnsGrindstone() {
        assertEquals(
                RepairMethod.GRINDSTONE,
                RepairCalculator.chooseMethod(
                        RepairCosts.defaults(), new RepairCalculator.Availability(false, true, false, true)),
                "Нет материала, но есть второй такой же инструмент — чиним точилом, иначе инструмент остаётся "
                        + "сломанным. При смене chooseMethod проверь строку обоих ниже в тесте");
    }

    @Test
    void chooseMethod_BothAvailable_CheapestWins() {
        assertEquals(
                RepairMethod.ANVIL,
                RepairCalculator.chooseMethod(
                        new RepairCosts(1, 2, 10), new RepairCalculator.Availability(true, true, false, true)),
                "Материал (1) + опыт (1×2) = 3 дешевле второго инструмента (10): выбираем наковальню, иначе расход "
                        + "неверен. При смене chooseMethod проверь строку точила ниже в тесте");
        assertEquals(
                RepairMethod.GRINDSTONE,
                RepairCalculator.chooseMethod(
                        new RepairCosts(1, 20, 10), new RepairCalculator.Availability(true, true, false, true)),
                "Материал (1) + опыт (1×20) = 21 дороже второго инструмента (10): выбираем точило, иначе переплата. "
                        + "При смене chooseMethod проверь строку зачарования ниже в тесте");
    }

    @Test
    void chooseMethod_Enchanted_RequiresAnvil() {
        assertEquals(
                RepairMethod.ANVIL,
                RepairCalculator.chooseMethod(
                        RepairCosts.defaults(), new RepairCalculator.Availability(true, true, true, true)),
                "Зачарованный инструмент чинится только наковальней: точило снимет чары, иначе чары теряются. При "
                        + "смене chooseMethod проверь строку без материала ниже в тесте");
        assertEquals(
                RepairMethod.NONE,
                RepairCalculator.chooseMethod(
                        RepairCosts.defaults(), new RepairCalculator.Availability(false, true, true, true)),
                "Зачарованный инструмент без материала не чинится вовсе: точило снимет чары, а наковальня без "
                        + "материала бесполезна, иначе ремонт невозможен. При смене Availability проверь строку "
                        + "крепкого ниже в тесте");
    }

    @Test
    void chooseMethod_NotDamagedEnough_ReturnsNone() {
        assertEquals(
                RepairMethod.NONE,
                RepairCalculator.chooseMethod(
                        RepairCosts.defaults(), new RepairCalculator.Availability(true, true, false, false)),
                "Крепкий инструмент не чиним даже при материале и паре: ресурс тратился бы зря, иначе ремонт целого. "
                        + "При смене chooseMethod проверь строку без материала ниже в тесте");
    }

    @Test
    void chooseMethod_NothingAvailable_ReturnsNone() {
        assertEquals(
                RepairMethod.NONE,
                RepairCalculator.chooseMethod(
                        RepairCosts.defaults(), new RepairCalculator.Availability(false, false, false, true)),
                "Без материала и без второго инструмента чинить нечем, иначе выбор падает. При смене chooseMethod "
                        + "проверь строку null ниже в тесте");
    }

    @Test
    void chooseMethod_NullCosts_Throws() {
        assertThrows(
                NullPointerException.class,
                () -> RepairCalculator.chooseMethod(null, new RepairCalculator.Availability(true, true, false, true)),
                "Стоимость без чисел не задать: выбор способа обязан падать, а не подставлять нули, иначе стоимость "
                        + "врёт. При смене проверки проверь строку выше в тесте");
    }
}
