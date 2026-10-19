package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class OverflowTuningProperties {

    @Property
    void stockFactor_MoreOnBase_NeverRaisesValue(
            @ForAll @IntRange(min = 0, max = 100_000) int low, @ForAll @IntRange(min = 1, max = 100_000) int more) {
        OverflowTuning tuning = OverflowTuning.defaults();
        int bigger = low + more;

        assertTrue(
                tuning.stockFactor(bigger) <= tuning.stockFactor(low),
                "Чем больше предметов на базе, тем ниже ценность: " + low + " → " + bigger + ", коэффициенты "
                        + tuning.stockFactor(low) + " → " + tuning.stockFactor(bigger));
    }

    @Property
    void effectiveUnitValue_AnyBaseAndStock_StaysBetweenZeroAndBase(
            @ForAll @IntRange(min = 0, max = 100_000) int base, @ForAll @IntRange(min = 0, max = 100_000) int stock) {
        OverflowTuning tuning = OverflowTuning.defaults();
        int value = tuning.effectiveUnitValue(base, stock);

        assertTrue(
                value >= 0, "Ценность не бывает отрицательной: база " + base + ", склад " + stock + ", итог " + value);
        assertTrue(
                value <= base,
                "Склад может только снижать ценность: база " + base + ", склад " + stock + ", итог " + value);
    }
}
