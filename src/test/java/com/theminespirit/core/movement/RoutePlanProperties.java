package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class RoutePlanProperties {
    @Property
    void next_AllButOneSkipped_StaysOnLast(
            @ForAll RoutePlan.Mode mode,
            @ForAll @IntRange(min = 1, max = 8) int size,
            @ForAll @IntRange(min = 0, max = 1024) int rawCurrent,
            @ForAll @IntRange(min = 0, max = 1024) int rawLone) {
        int current = Math.floorMod(rawCurrent, size);
        int lone = Math.floorMod(rawLone, size);
        Set<Integer> skipped = new HashSet<>();
        for (int i = 0; i < size; i++) {
            if (i != lone) {
                skipped.add(i);
            }
        }
        List<BlockCoord> points = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            points.add(new BlockCoord(i, 0, i));
        }

        assertEquals(
                lone,
                new RoutePlan(points, mode).next(current, skipped).orElseThrow(),
                "Когда пропущены все точки, кроме одной, маршрут обязан стоять на ней: иначе патруль "
                        + "с недостижимыми точками виснет вместо обхода единственной доступной");
    }
}
