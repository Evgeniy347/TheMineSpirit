package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class ReplantPlanProperties {
    @Property
    void of_AnyTrunkAndStock_PlantsOnlyTrunkCellsWithinStock(
            @ForAll("footprint") List<BlockCoord> trunk, @ForAll("stock") int available) {
        ReplantPlan plan = ReplantPlan.of(trunk, available, ReplantData.defaults());

        for (BlockCoord cell : plan.cells()) {
            assertTrue(
                    trunk.contains(cell),
                    "Посаженная клетка " + cell + " обязана быть клеткой ствола " + trunk
                            + ": иначе саженец встанет мимо места дерева");
        }
        assertTrue(
                plan.cells().size() <= Math.max(1, available),
                "Посадить больше саженцев, чем есть (" + available + "), нельзя: план предлагает "
                        + plan.cells().size() + " клеток");
    }

    @Property
    void of_AnyTrunkAndStock_CellsEmptyExactlyWhenNoSaplings(
            @ForAll("footprint") List<BlockCoord> trunk, @ForAll("stock") int available) {
        ReplantPlan plan = ReplantPlan.of(trunk, available, ReplantData.defaults());

        assertEquals(
                available < 1,
                plan.cells().isEmpty(),
                "Пустой список клеток обязан быть ровно при нуле саженцев: иначе дух либо не использует "
                        + "имеющиеся саженцы, либо сажает без них");
    }

    @Property
    void of_SquareWithEnoughSaplings_PlantsWholeSquare(@ForAll("squareOffset") BlockCoord offset) {
        List<BlockCoord> square = squareAt(offset);

        ReplantPlan plan = ReplantPlan.of(square, ReplantData.defaults().squareSaplings(), ReplantData.defaults());

        assertEquals(
                List.copyOf(square.stream().sorted().toList()),
                plan.cells(),
                "Квадрат 2x2 при достатке саженцев обязан засаживаться целиком: иначе гигантское дерево "
                        + "не восстановить");
        assertTrue(plan.square(), "Квадрат 2x2 обязан отмечаться признаком square");
    }

    @Provide
    Arbitrary<List<BlockCoord>> footprint() {
        return Combinators.combine(
                        Arbitraries.integers().between(-5, 5),
                        Arbitraries.integers().between(60, 70),
                        Arbitraries.integers().between(-5, 5),
                        Arbitraries.of(true, false))
                .as((x, y, z, square) -> square ? squareAt(new BlockCoord(x, y, z)) : List.of(new BlockCoord(x, y, z)));
    }

    @Provide
    Arbitrary<Integer> stock() {
        return Arbitraries.integers().between(0, 8);
    }

    @Provide
    Arbitrary<BlockCoord> squareOffset() {
        return Combinators.combine(
                        Arbitraries.integers().between(-5, 5),
                        Arbitraries.integers().between(60, 70),
                        Arbitraries.integers().between(-5, 5))
                .as(BlockCoord::new);
    }

    private static List<BlockCoord> squareAt(BlockCoord offset) {
        List<BlockCoord> cells = new ArrayList<>(4);
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                cells.add(new BlockCoord(offset.x() + dx, offset.y(), offset.z() + dz));
            }
        }
        return cells;
    }
}
