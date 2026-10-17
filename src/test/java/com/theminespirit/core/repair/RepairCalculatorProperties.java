package com.theminespirit.core.repair;

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
    void chooseMethod_Enchanted_NeverGrindstone(
            @ForAll @IntRange(min = 0, max = 10000) int bestDurability,
            @ForAll @IntRange(min = 1, max = 10000) int maxDurability,
            @ForAll @IntRange(min = 1, max = 100) int percent) {
        assertNotEquals(RepairMethod.GRINDSTONE,
                RepairCalculator.chooseMethod(2, true, bestDurability, maxDurability, percent),
                "зачарованный предмет не должен попадать в точило ни при каких числах: чары потерялись бы");
    }
}
