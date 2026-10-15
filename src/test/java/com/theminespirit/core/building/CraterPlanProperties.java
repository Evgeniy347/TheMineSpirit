package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class CraterPlanProperties {
    private static final BlockCoord CENTER = new BlockCoord(0, 64, 0);
    private static final int RADIUS = 2;
    private static final int LEVEL = 64;
    private static final int MAX_DEPTH = 4;

    @Provide
    Arbitrary<List<Integer>> depths() {
        return Arbitraries.integers().between(0, MAX_DEPTH).list().ofSize(columns().size());
    }

    @Provide
    Arbitrary<Set<BlockCoord>> protectedCells() {
        Arbitrary<BlockCoord> any = Combinators.combine(
                Arbitraries.integers().between(-RADIUS, RADIUS),
                Arbitraries.integers().between(LEVEL - MAX_DEPTH, LEVEL),
                Arbitraries.integers().between(-RADIUS, RADIUS)).as(BlockCoord::new);
        return any.list().ofMaxSize(4).map(HashSet::new);
    }

    private static List<BlockCoord> columns() {
        List<BlockCoord> columns = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz <= RADIUS * RADIUS) {
                    columns.add(new BlockCoord(dx, 0, dz));
                }
            }
        }
        return columns;
    }

    private static TestCraterSurface surface(List<Integer> depths, Set<BlockCoord> protectedCells) {
        TestCraterSurface surface = new TestCraterSurface(CENTER, RADIUS);
        List<BlockCoord> columns = columns();
        for (BlockCoord column : columns) {
            surface.level(column.x(), column.z(), LEVEL);
        }
        for (int index = 0; index < columns.size(); index++) {
            int depth = depths.get(index);
            if (depth > 0) {
                surface.hole(columns.get(index).x(), columns.get(index).z(), depth);
            }
        }
        protectedCells.forEach(surface::protect);
        return surface;
    }

    private static boolean inDisk(BlockCoord pos) {
        int dx = pos.x() - CENTER.x();
        int dz = pos.z() - CENTER.z();
        return dx * dx + dz * dz <= RADIUS * RADIUS;
    }

    @Property
    void plan_AfterFill_NoFillableCellBelowGroundRemainsEmpty(@ForAll("depths") List<Integer> depths,
            @ForAll("protectedCells") Set<BlockCoord> protectedCells) {
        TestCraterSurface surface = surface(depths, protectedCells);
        CraterPlan.Plan plan = CraterPlan.plan(surface);
        Set<BlockCoord> filled = new HashSet<>(plan.cells());

        for (BlockCoord column : columns()) {
            int x = CENTER.x() + column.x();
            int z = CENTER.z() + column.z();
            for (int y = LEVEL; y >= LEVEL - MAX_DEPTH; y--) {
                BlockCoord cell = new BlockCoord(x, y, z);
                if (surface.isAir(x, y, z) && !surface.isProtected(x, y, z)) {
                    assertTrue(filled.contains(cell),
                            "После засыпки в клетке " + cell + " ниже уровня земли не должно остаться пустоты: "
                                    + "иначе яма не закрыта");
                }
            }
        }
    }

    @Property
    void plan_BlockCount_EqualsNumberOfEmptyPlanCells(@ForAll("depths") List<Integer> depths,
            @ForAll("protectedCells") Set<BlockCoord> protectedCells) {
        CraterPlan.Plan plan = CraterPlan.plan(surface(depths, protectedCells));

        assertEquals(plan.cells().size(), plan.blockCount(),
                "Расход блоков обязан совпасть с числом пустых клеток плана: иначе дух берёт лишнее или недобирает");
    }

    @Property
    void plan_AnyInput_CellsAreBottomUpAndInsideDisk(@ForAll("depths") List<Integer> depths,
            @ForAll("protectedCells") Set<BlockCoord> protectedCells) {
        CraterPlan.Plan plan = CraterPlan.plan(surface(depths, protectedCells));

        for (BlockCoord cell : plan.cells()) {
            assertTrue(inDisk(cell),
                    "Клетка засыпки " + cell + " обязана лежать в диске взрыва: иначе дух засыпает мимо");
        }
        int previousY = Integer.MIN_VALUE;
        for (BlockCoord cell : plan.cells()) {
            assertTrue(cell.y() >= previousY,
                    "Клетки плана обязаны идти снизу вверх: иначе верхний блок повиснет без опоры");
            previousY = cell.y();
        }
    }

    @Property
    void plan_SameInput_TwiceSameResult(@ForAll("depths") List<Integer> depths,
            @ForAll("protectedCells") Set<BlockCoord> protectedCells) {
        assertEquals(CraterPlan.plan(surface(depths, protectedCells)),
                CraterPlan.plan(surface(depths, protectedCells)),
                "Тот же вход даёт тот же план: иначе число блоков и постановка расходятся");
    }
}