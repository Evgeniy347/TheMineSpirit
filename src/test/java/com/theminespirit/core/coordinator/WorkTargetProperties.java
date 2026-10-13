package com.theminespirit.core.coordinator;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.theminespirit.core.observation.BlockCoord;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class WorkTargetProperties {
    @Property
    void target_KindsWithSameAnchor_AreDifferentKeys(@ForAll @IntRange(min = -1000, max = 1000) int x,
            @ForAll @IntRange(min = -64, max = 320) int y, @ForAll @IntRange(min = -1000, max = 1000) int z) {
        BlockCoord anchor = new BlockCoord(x, y, z);

        assertNotEquals(new WorkTarget(WorkKind.CELL, anchor), new WorkTarget(WorkKind.TREE, anchor),
                "Клетка и дерево с одним якорем — разные цели: иначе бронь рубки заняла бы клетку стройки");
        assertNotEquals(new WorkTarget(WorkKind.TREE, anchor), new WorkTarget(WorkKind.VEIN, anchor),
                "Дерево и жила с одним якорем — разные цели: иначе бронь шахты заняла бы ствол рубки");
        assertNotEquals(new WorkTarget(WorkKind.CELL, anchor), new WorkTarget(WorkKind.VEIN, anchor),
                "Клетка и жила с одним якорем — разные цели: иначе бронь шахты заняла бы клетку стройки");
    }
}
