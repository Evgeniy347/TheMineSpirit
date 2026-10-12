package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

class ZoneProperties {
    @Provide
    Arbitrary<BlockCoord> coords() {
        Arbitrary<Integer> axis = Arbitraries.integers().between(-300, 300);
        return Combinators.combine(axis, axis, axis).as(BlockCoord::new);
    }

    @Property(tries = 200)
    void nearestInside_AnyPosition_IsInsideTheZone(@ForAll("coords") BlockCoord center, @ForAll @IntRange(min = 1, max = 64) int radius,
                                                   @ForAll("coords") BlockCoord pos) {
        WorkZone zone = new WorkZone(center, radius);

        BlockCoord inside = zone.nearestInside(pos);

        assertTrue(zone.box().contains(inside), "Ближайшая точка внутри обязана лежать в зоне, иначе возврат вернёт духа за границу. Точка: " + inside);
    }

    @Property(tries = 200)
    void nearestInside_PositionInsideTheZone_IsTheSamePoint(@ForAll("coords") BlockCoord center, @ForAll @IntRange(min = 1, max = 64) int radius,
                                                            @ForAll @IntRange(min = -64, max = 64) int dx, @ForAll @IntRange(min = -64, max = 64) int dz) {
        WorkZone zone = new WorkZone(center, radius);
        BlockCoord pos = new BlockCoord(center.x() + Math.max(-radius, Math.min(radius, dx)), center.y(), center.z() + Math.max(-radius, Math.min(radius, dz)));

        assertEquals(pos, zone.nearestInside(pos), "Позиция в зоне — сама себе ближайшая точка: иначе дух в зоне получал бы команду сместиться");
        assertEquals(new ZonePolicy.Allowed(), ZonePolicy.checkTarget(pos, zone), "Позиция в зоне разрешена как цель");
    }

    @Property(tries = 200)
    void excess_AnyPosition_EqualsLargestAxisDistanceToNearestInside(@ForAll("coords") BlockCoord center, @ForAll @IntRange(min = 1, max = 64) int radius,
                                                                    @ForAll("coords") BlockCoord pos) {
        WorkZone zone = new WorkZone(center, radius);
        BlockCoord inside = zone.nearestInside(pos);

        int expected = Math.max(Math.abs(pos.x() - inside.x()), Math.max(Math.abs(pos.y() - inside.y()), Math.abs(pos.z() - inside.z())));

        assertEquals(expected, zone.excess(pos), "Выход за границу — наибольшее расстояние по оси до ближайшей точки внутри: по нему считается допуск и текст отказа");
    }
}
