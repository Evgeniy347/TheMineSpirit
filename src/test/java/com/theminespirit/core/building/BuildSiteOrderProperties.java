package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.BuildSiteOrder.Step;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class BuildSiteOrderProperties {
    private record Box(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ, int doorwayZ) {
    }

    @Provide
    Arbitrary<Box> boxes() {
        return Combinators.combine(
                Arbitraries.integers().between(-20, 20),
                Arbitraries.integers().between(0, 60),
                Arbitraries.integers().between(-20, 20),
                Arbitraries.integers().between(3, 9),
                Arbitraries.integers().between(2, 5),
                Arbitraries.integers().between(3, 9),
                Arbitraries.integers().between(1, 3))
                .as(Box::new);
    }

    private static List<BlockCoord> hollowBox(Box box) {
        int maxX = box.minX() + box.sizeX() - 1;
        int maxY = box.minY() + box.sizeY() - 1;
        int maxZ = box.minZ() + box.sizeZ() - 1;
        List<BlockCoord> cells = new ArrayList<>();
        for (int y = box.minY(); y <= maxY; y++) {
            for (int x = box.minX(); x <= maxX; x++) {
                for (int z = box.minZ(); z <= maxZ; z++) {
                    boolean boundary = x == box.minX() || x == maxX || z == box.minZ() || z == maxZ;
                    boolean roof = y == maxY;
                    if (roof || (boundary && y < maxY)) {
                        cells.add(new BlockCoord(x, y, z));
                    }
                }
            }
        }
        return cells;
    }

    private static BlockCoord doorway(Box box) {
        return new BlockCoord(box.minX() + box.doorwayZ(), box.minY(), box.minZ());
    }

    @Property
    void plan_ClosedBoxWithDoorway_IsPermutation(@ForAll("boxes") Box box) {
        List<BlockCoord> cells = hollowBox(box);
        BlockCoord doorway = doorway(box);

        List<Step> plan = BuildSiteOrder.plan(cells, doorway);

        Set<BlockCoord> expected = new HashSet<>(cells);
        expected.add(doorway);
        Set<BlockCoord> actual = new HashSet<>();
        for (Step step : plan) {
            actual.add(step.cell());
        }
        assertEquals(expected, actual,
                "План обязан быть перестановкой клеток коробки плюс проём: потеря — дыра, дубль — двойной расход");
        assertEquals(expected.size(), plan.size(),
                "Число шагов обязано равняться числу клеток: иначе часть коробки не строится");
    }

    @Property
    void plan_AnyStep_StandDiffersFromCell(@ForAll("boxes") Box box) {
        List<Step> plan = BuildSiteOrder.plan(hollowBox(box), doorway(box));

        for (Step step : plan) {
            assertFalse(step.stand().equals(step.cell()),
                    "Стоянка не может совпасть с клеткой постановки: дух окажется замурован в собственном блоке");
        }
    }

    @Property
    void plan_AnyStep_StandNotFutureTarget(@ForAll("boxes") Box box) {
        List<BlockCoord> cells = hollowBox(box);
        List<Step> plan = BuildSiteOrder.plan(cells, doorway(box));

        Set<BlockCoord> placed = new HashSet<>();
        for (Step step : plan) {
            assertFalse(cells.contains(step.stand()) && !placed.contains(step.stand()),
                    "Стоянка не может быть ещё не поставленной клеткой: дух стоял бы там, куда обязан поставить блок");
            placed.add(step.cell());
        }
    }

    @Property
    void plan_ClosedBoxWithRoof_RoofAfterWalls(@ForAll("boxes") Box box) {
        List<BlockCoord> cells = hollowBox(box);
        List<Step> plan = BuildSiteOrder.plan(cells, doorway(box));
        int roofY = box.minY() + box.sizeY() - 1;

        boolean sawRoof = false;
        for (Step step : plan) {
            if (step.cell().equals(doorway(box))) {
                continue;
            }
            if (step.cell().y() == roofY) {
                sawRoof = true;
            } else {
                assertFalse(sawRoof,
                        "Стена после крыши: крыша без стен строителю не на что опереться");
            }
        }
    }

    @Property
    void plan_Doorway_Last(@ForAll("boxes") Box box) {
        List<Step> plan = BuildSiteOrder.plan(hollowBox(box), doorway(box));

        assertTrue(!plan.isEmpty(), "План непустой коробки не может быть пустым");
        assertEquals(doorway(box), plan.get(plan.size() - 1).cell(),
                "Блок проёма обязан ставиться последним: иначе дух замурует себя внутри постройки");
    }

    @Property
    void plan_SameInput_TwiceSameResult(@ForAll("boxes") Box box) {
        List<BlockCoord> cells = hollowBox(box);
        BlockCoord doorway = doorway(box);
        assertEquals(BuildSiteOrder.plan(cells, doorway), BuildSiteOrder.plan(cells, doorway),
                "Тот же вход даёт тот же план: иначе бронь клеток не сходится с постановкой");
    }
}