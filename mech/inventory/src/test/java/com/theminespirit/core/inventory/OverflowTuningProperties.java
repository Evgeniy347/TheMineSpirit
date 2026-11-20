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
                "Чем больше предметов на базе, тем ниже ценность, иначе склад обесценивает добычу обратным ходом: "
                        + low + " → " + bigger + ", коэффициенты "
                        + tuning.stockFactor(low) + " → " + tuning.stockFactor(bigger)
                        + ". При смене stockFactor проверь границу роста склада");
    }

    @Property
    void effectiveUnitValue_AnyBaseAndStock_StaysBetweenZeroAndBase(
            @ForAll @IntRange(min = 0, max = 100_000) int base, @ForAll @IntRange(min = 0, max = 100_000) int stock) {
        OverflowTuning tuning = OverflowTuning.defaults();
        int value = tuning.effectiveUnitValue(base, stock);

        assertTrue(
                value >= 0,
                "Ценность не бывает отрицательной, иначе сравнение выгод ломается и дух бегает к бессмыслице: база "
                        + base + ", склад " + stock + ", итог " + value
                        + ". При смене effectiveUnitValue проверь нижнюю границу");
        assertTrue(
                value <= base,
                "Склад может только снижать ценность, иначе большие запасы выгоднее добычи и план растёт: база "
                        + base + ", склад " + stock + ", итог " + value
                        + ". При смене effectiveUnitValue проверь верхнюю границу");
    }
}
