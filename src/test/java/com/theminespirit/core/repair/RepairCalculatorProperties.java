package com.theminespirit.core.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class RepairCalculatorProperties {

    @Property
    void grindstoneResult_AnyDurabilities_NeverExceedsMax(
            @ForAll @IntRange(min = 0, max = 10000) int durabilityA,
            @ForAll @IntRange(min = 0, max = 10000) int durabilityB,
            @ForAll @IntRange(min = 1, max = 10000) int maxDurability) {
        int result = RepairCalculator.grindstoneResult(durabilityA, durabilityB, maxDurability);
        assertTrue(result <= maxDurability,
                "результат ремонта " + result + " превышает максимальную прочность " + maxDurability);
        assertTrue(result >= 0, "результат ремонта " + result + " отрицателен");
    }

    @Property
    void anvilResult_AnyDurability_NeverExceedsMax(
            @ForAll @IntRange(min = 0, max = 10000) int durability,
            @ForAll @IntRange(min = 1, max = 10000) int maxDurability,
            @ForAll @IntRange(min = 1, max = 100) int percent) {
        int result = RepairCalculator.anvilResult(durability, maxDurability, percent);
        assertTrue(result <= maxDurability,
                "результат ремонта наковальней " + result + " превышает максимальную прочность " + maxDurability);
        assertTrue(result >= 0, "результат ремонта наковальней " + result + " отрицателен");
    }

    @Property
    void chooseMethod_Enchanted_NeverGrindstone(
            @ForAll boolean materialAvailable,
            @ForAll boolean secondToolAvailable) {
        assertNotEquals(RepairMethod.GRINDSTONE,
                RepairCalculator.chooseMethod(RepairCosts.defaults(),
                        new RepairCalculator.Availability(materialAvailable, secondToolAvailable, true, true)),
                "зачарованный предмет не должен попадать в точило ни при каких флагах: чары потерялись бы");
    }

    @Property
    void chooseMethod_NotDamaged_NeverRepairs(
            @ForAll @IntRange(min = 0, max = 100) int xpWeight,
            @ForAll boolean materialAvailable,
            @ForAll boolean secondToolAvailable,
            @ForAll boolean enchanted) {
        assertEquals(RepairMethod.NONE,
                RepairCalculator.chooseMethod(new RepairCosts(1, xpWeight, 10),
                        new RepairCalculator.Availability(materialAvailable, secondToolAvailable, enchanted, false)),
                "крепкий предмет не чинится ни одним способом: ресурс тратился бы впустую");
    }

    @Property
    void chooseMethod_BothWaysAvailable_ChoosesCheaper(
            @ForAll @IntRange(min = 0, max = 100) int xpWeight) {
        RepairCosts costs = new RepairCosts(1, xpWeight, 10);
        RepairMethod method = RepairCalculator.chooseMethod(costs, new RepairCalculator.Availability(true, true, false, true));
        RepairMethod cheaper = costs.anvilCost() <= costs.toolCost() ? RepairMethod.ANVIL : RepairMethod.GRINDSTONE;

        assertEquals(cheaper, method,
                "при обоих способах выбран " + method + ", а дешевле " + cheaper
                        + ": доска и скрипт обязаны брать тот же способ, что и расчёт стоимости");
    }
}
