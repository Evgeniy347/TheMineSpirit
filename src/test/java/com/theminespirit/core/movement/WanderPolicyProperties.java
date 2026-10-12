package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class WanderPolicyProperties {
    @Property
    void next_SameSeed_SamePoint(
            @ForAll long seed,
            @ForAll @IntRange(min = -1000, max = 1000) int x,
            @ForAll @IntRange(min = -64, max = 319) int y,
            @ForAll @IntRange(min = -1000, max = 1000) int z,
            @ForAll @IntRange(min = 1, max = 32) int radius,
            @ForAll @IntRange(min = 0, max = 64) int attempt) {
        BlockCoord origin = new BlockCoord(x, y, z);

        assertEquals(WanderPolicy.next(seed, origin, radius, attempt),
                WanderPolicy.next(seed, origin, radius, attempt),
                "Два вызова с тем же сидом обязаны давать ту же точку: иначе блуждание нетестируемо");
    }

    @Property
    void next_AnySeed_PointInsideRadius(
            @ForAll long seed,
            @ForAll @IntRange(min = -1000, max = 1000) int x,
            @ForAll @IntRange(min = -64, max = 319) int y,
            @ForAll @IntRange(min = -1000, max = 1000) int z,
            @ForAll @IntRange(min = 1, max = 32) int radius,
            @ForAll @IntRange(min = 0, max = 64) int attempt) {
        BlockCoord origin = new BlockCoord(x, y, z);

        BlockCoord point = WanderPolicy.next(seed, origin, radius, attempt);

        assertTrue(Math.abs(point.x() - x) <= radius && Math.abs(point.z() - z) <= radius
                && Math.abs(point.y() - y) <= WanderPolicy.VERTICAL_SPREAD,
                "Точка блуждания обязана лежать внутри радиуса от старта и в пределах ±2 по высоте: "
                        + "иначе дух уходит из порученной зоны. Точка: " + point);
    }
}
