package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Optional;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class FarmJobProperties {
    @Property
    void eligible_AnyTile_NeverHarvestAndBoneMealTogether(@ForAll Ground ground, @ForAll Above above,
            @ForAll CropKind kind, @ForAll @IntRange(min = 0, max = 7) int age, @ForAll boolean watered) {
        FarmTile tile = tile(ground, above, kind, age);

        boolean harvestable = FarmJob.HARVEST.eligible(tile, CropKind.ANY, watered);
        boolean feedable = FarmJob.BONE_MEAL.eligible(tile, CropKind.ANY, watered);

        assertFalse(harvestable && feedable,
                "Клетка не может одновременно требовать сбор и подкормку: зрелость едина, "
                        + "иначе планировщик сломает зрелый посев мукой или удобрит вместо сбора. Клетка: " + tile);
    }

    private static FarmTile tile(Ground ground, Above above, CropKind kind, int age) {
        if (above != Above.CROP) {
            return new FarmTile(ground, above, Optional.empty());
        }
        CropKind concrete = kind == CropKind.ANY ? CropKind.WHEAT : kind;
        int clamped = Math.min(age, concrete.maxAge());
        return new FarmTile(ground, above, Optional.of(new CropState(concrete, clamped)));
    }
}
