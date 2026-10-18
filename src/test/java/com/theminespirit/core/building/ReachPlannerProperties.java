package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class ReachPlannerProperties {
    private static final SpiritReach REACH = new SpiritReach(3, 15, 3);

    @Provide
    Arbitrary<List<BlockCoord>> cells() {
        return Arbitraries.integers().between(-6, 6)
                .flatMap(x -> Arbitraries.integers().between(0, 6)
                        .flatMap(y -> Arbitraries.integers().between(-6, 6)
                                .map(z -> new BlockCoord(x, y, z))))
                .list()
                .ofMaxSize(20)
                .map(cells -> List.copyOf(new LinkedHashSet<>(cells)));
    }

    @Provide
    Arbitrary<BlockCoord> feet() {
        return Arbitraries.integers().between(-10, 10)
                .flatMap(x -> Arbitraries.integers().between(-2, 8)
                        .flatMap(y -> Arbitraries.integers().between(-10, 10)
                                .map(z -> new BlockCoord(x, y, z))));
    }

    @Property
    void workableNow_AnyTargets_ReturnsExactlyReachableReadyCellsOutsideBody(
            @ForAll("feet") BlockCoord feet, @ForAll("cells") List<BlockCoord> cells) {
        Set<BlockCoord> ready = new HashSet<>();
        for (int i = 0; i < cells.size(); i += 2) {
            ready.add(cells.get(i));
        }
        ReachPlanner.World world = new ReachPlannerTests.SetWorld(ready, Set.of());

        List<BlockCoord> workable = ReachPlanner.workableNow(REACH, feet, cells, world);

        List<BlockCoord> expected = cells.stream()
                .filter(cell -> REACH.reaches(feet, cell))
                .filter(cell -> !ReachPlanner.inBody(feet, cell))
                .filter(ready::contains)
                .toList();
        assertEquals(expected, workable,
                "С места делается ровно то, что досягаемо, готово и не в теле духа, в порядке вызывающего: пропуск "
                        + "или лишняя клетка ломают постановку без подходов");
    }

    @Property
    void nextStand_AnyTargets_StandIsFreeOutsideSchemeAndCoversSomething(
            @ForAll("feet") BlockCoord feet, @ForAll("cells") List<BlockCoord> cells) {
        Set<BlockCoord> scheme = Set.copyOf(cells);
        ReachPlanner.World world = new ReachPlanner.World() {
            @Override
            public boolean ready(BlockCoord cell) {
                return true;
            }

            @Override
            public boolean standable(BlockCoord spot) {
                return spot.y() == 0 || spot.y() == 3;
            }
        };

        Optional<BlockCoord> stand = ReachPlanner.nextStand(REACH, feet, cells, world, Set.of(), scheme);

        if (stand.isEmpty()) {
            return;
        }
        BlockCoord spot = stand.get();
        assertTrue(world.standable(spot), "Стоянка обязана быть клеткой, где можно стоять");
        assertFalse(scheme.contains(spot) || scheme.contains(new BlockCoord(spot.x(), spot.y() + 1, spot.z())),
                "Стоянка и голова духа не могут быть клетками схемы: дух встанет в будущий блок, фактически " + spot);
        assertFalse(spot.equals(feet), "Стоянка не совпадает с текущей: иначе подход не начнётся");
        assertTrue(cells.stream().anyMatch(cell -> REACH.reaches(spot, cell) && !SpiritReach.forbiddenStand(spot, cell)),
                "Со стоянки обязана быть досягаема хотя бы одна клетка: иначе шаг бесполезен");
    }

    @Property
    void plan_AnyTargetsWithGround_FinishesWithoutCycle(@ForAll("cells") List<BlockCoord> cells) {
        Set<BlockCoord> scheme = Set.copyOf(cells);
        ReachPlanner.World world = new ReachPlanner.World() {
            @Override
            public boolean ready(BlockCoord cell) {
                return true;
            }

            @Override
            public boolean standable(BlockCoord spot) {
                return !scheme.contains(spot);
            }
        };
        Set<BlockCoord> remaining = new LinkedHashSet<>(cells);
        BlockCoord feet = new BlockCoord(-20, 0, -20);
        Set<BlockCoord> excluded = new HashSet<>();
        int steps = 0;
        int limit = cells.size() * 4 + 50;
        while (!remaining.isEmpty()) {
            assertTrue(steps++ < limit, "Планировщик обязан завершить работу за конечное число шагов: зацикливание "
                    + "на стоянках оставило бы духа бегать вокруг дома, осталось " + remaining.size() + " клеток");
            List<BlockCoord> pending = new ArrayList<>(remaining);
            List<BlockCoord> workable = ReachPlanner.workableNow(REACH, feet, pending, world);
            if (!workable.isEmpty()) {
                workable.forEach(remaining::remove);
                excluded.clear();
                continue;
            }
            Optional<BlockCoord> stand = ReachPlanner.nextStand(REACH, feet, pending, world, excluded, Set.of());
            assertTrue(stand.isPresent(), "Пока есть клетки, обязана найтись следующая стоянка: иначе стройка "
                    + "брошена на " + remaining.size() + " клетках");
            feet = stand.get();
            excluded.add(feet);
        }
    }
}
