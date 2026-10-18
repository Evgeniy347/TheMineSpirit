package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class AreaReplantProperties {
    @Property
    void plan_AnyCenter_EveryCellIsOnGridWithinRadius(@ForAll("center") BlockCoord center) {
        ReplantData data = ReplantData.defaults();
        TreeView view = flatSoilView(center.y());

        List<BlockCoord> cells = AreaReplant.plan(center, List.of(center), data, view);

        for (BlockCoord cell : cells) {
            int dx = cell.x() - center.x();
            int dz = cell.z() - center.z();
            assertTrue(dx % data.areaStep() == 0 && dz % data.areaStep() == 0,
                    "Клетка " + cell + " смещена от центра " + center + " не на шаг сетки " + data.areaStep());
            assertTrue(Math.abs(dx) <= data.areaRadius() && Math.abs(dz) <= data.areaRadius(),
                    "Клетка " + cell + " вышла за радиус досадки " + data.areaRadius());
            assertTrue(!cell.equals(center),
                    "Клетка ствола " + center + " не может попасть в досадку");
        }
    }

    @Property
    void plan_AnyCenter_CellCountMatchesGridMinusCenter(@ForAll("center") BlockCoord center) {
        ReplantData data = ReplantData.defaults();
        TreeView view = flatSoilView(center.y());

        long expected = 0;
        for (int dx = -data.areaRadius(); dx <= data.areaRadius(); dx += data.areaStep()) {
            for (int dz = -data.areaRadius(); dz <= data.areaRadius(); dz += data.areaStep()) {
                if (dx != 0 || dz != 0) {
                    expected++;
                }
            }
        }

        List<BlockCoord> cells = AreaReplant.plan(center, List.of(center), data, view);

        assertTrue(cells.size() == expected,
                "На ровной почве без построек в план входят все клетки сетки кроме клетки ствола: ожидалось "
                        + expected + ", получено " + cells.size());
    }

    private static TreeView flatSoilView(int soilY) {
        return new TreeView() {
            @Override
            public boolean isLog(BlockCoord coord) {
                return false;
            }

            @Override
            public boolean isNaturalLeaves(BlockCoord coord) {
                return false;
            }

            @Override
            public boolean isSoil(BlockCoord coord) {
                return coord.y() == soilY - 1;
            }

            @Override
            public boolean isArtificial(BlockCoord coord) {
                return false;
            }

            @Override
            public boolean isEmpty(BlockCoord coord) {
                return true;
            }
        };
    }

    @Provide
    Arbitrary<BlockCoord> center() {
        return Combinators.combine(Arbitraries.integers().between(-30, 30), Arbitraries.integers().between(0, 100),
                Arbitraries.integers().between(-30, 30)).as(BlockCoord::new);
    }
}
