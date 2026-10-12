package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class FuelTableProperties {
    @Property
    void fuelNeeded_AnyCountAndTimes_CoversCookTimeWithMinimalFuel(
            @ForAll @IntRange(min = 1, max = 256) int count,
            @ForAll @IntRange(min = 1, max = 2000) int burn,
            @ForAll @IntRange(min = 1, max = 400) int cook) {
        int needed = FuelTable.fuelNeeded(count, burn, cook);

        long demand = (long) count * cook;
        assertTrue((long) needed * burn >= demand,
                "Топлива обязано хватать: " + needed + " × " + burn + " тиков горения < " + demand
                        + " тиков плавки, печь встанет посреди работы");
        assertTrue((long) (needed - 1) * burn < demand,
                "Топлива должно быть минимально: на единицу меньше всё ещё хватало бы (" + needed + " × " + burn
                        + " ≥ " + demand + "), значит дух сжигает лишнее");
    }
}
