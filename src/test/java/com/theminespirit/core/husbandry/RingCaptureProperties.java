package com.theminespirit.core.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.husbandry.RingGround.Cell;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class RingCaptureProperties {
    @Provide
    Arbitrary<int[][]> smallTerrain() {
        return terrain(5);
    }

    @Provide
    Arbitrary<int[][]> largeTerrain() {
        return terrain(6);
    }

    private static Arbitrary<int[][]> terrain(int size) {
        return Arbitraries.integers().between(0, 40)
                .flatMap(base -> Arbitraries.integers().between(0, 3).list().ofSize(size * size)
                        .map(deltas -> {
                            int[][] heights = new int[size][size];
                            for (int i = 0; i < deltas.size(); i++) {
                                heights[i / size][i % size] = base + deltas.get(i);
                            }
                            return heights;
                        }));
    }

    @Property
    void planSmall_AnyTerrainWithinThree_NoEscape(@ForAll("smallTerrain") int[][] heights) {
        RingGround ground = RingGround.of(-2, -2, heights);
        RingPlan.Ring ring = RingPlan.plan(ground, new Cell(0, 0), RingPlan.Size.SMALL);

        Optional<EscapeCheck.WeakSpot> escape = EscapeCheck.findEscape(ground, ring.tops(), ring.interior());

        assertTrue(escape.isEmpty(),
                "Кольцо обязано держать на любом рельефе с перепадом до 3: иначе дух строит кольцо, "
                        + "а животное уходит");
    }

    @Property
    void planLarge_AnyTerrainWithinThree_NoEscape(@ForAll("largeTerrain") int[][] heights) {
        RingGround ground = RingGround.of(-2, -2, heights);
        RingPlan.Ring ring = RingPlan.plan(ground, new Cell(0, 0), RingPlan.Size.LARGE);

        Optional<EscapeCheck.WeakSpot> escape = EscapeCheck.findEscape(ground, ring.tops(), ring.interior());

        assertTrue(escape.isEmpty(),
                "Большое кольцо обязано держать на любом рельефе с перепадом до 3: иначе дух строит кольцо, "
                        + "а крупное животное уходит");
    }

    @Provide
    Arbitrary<RingPlan.Size> ringSize() {
        return Arbitraries.of(RingPlan.Size.values());
    }

    @Property
    void missingTopBlockOnFlat_AnyThroughWallCell_ExitThroughThatCell(@ForAll("ringSize") RingPlan.Size size,
            @ForAll int wallIndex) {
        RingGround flat = size == RingPlan.Size.SMALL
                ? RingGround.flat(-2, -2, 5, 5, 10)
                : RingGround.flat(-2, -2, 6, 6, 10);
        Cell animal = new Cell(0, 0);
        RingPlan.Ring ring = RingPlan.plan(flat, animal, size);
        List<Cell> walls = ring.wall().stream()
                .filter(cell -> ring.interior().stream()
                        .anyMatch(inner -> Math.abs(inner.x() - cell.x()) + Math.abs(inner.z() - cell.z()) == 1))
                .sorted((first, second) -> {
                    int byX = Integer.compare(first.x(), second.x());
                    return byX != 0 ? byX : Integer.compare(first.z(), second.z());
                }).toList();
        Cell removed = walls.get(Math.floorMod(wallIndex, walls.size()));
        Map<Cell, Integer> tops = new HashMap<>(ring.tops());
        tops.put(removed, tops.get(removed) - 1);

        Optional<EscapeCheck.WeakSpot> escape = EscapeCheck.findEscape(flat, tops, ring.interior());

        assertTrue(escape.isPresent(),
                "Снятый верхний блок проходной стенки на ровной земле обязан выпускать: стенка в 1 блок "
                        + "берётся прыжком");
        assertEquals(removed, escape.orElseThrow().cell(),
                "Слабое место обязано указывать на разобранную стенку: иначе дух чинит не ту стенку");
    }
}
