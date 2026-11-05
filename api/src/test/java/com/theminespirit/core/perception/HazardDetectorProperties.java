package com.theminespirit.core.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class HazardDetectorProperties {
    private static final BlockCoord STAND = new BlockCoord(0, 5, 0);
    private static final int NEIGHBOURHOOD = 2 + 4 * (1 + HazardDetector.DROP_DEPTH);

    @Provide
    Arbitrary<List<Terrain>> neighbourhoods() {
        return Arbitraries.of(Terrain.class).list().ofSize(NEIGHBOURHOOD);
    }

    private static GridTerrain terrainOf(List<Terrain> cells) {
        GridTerrain terrain = new GridTerrain(Terrain.AIR);
        terrain.with(STAND.x(), STAND.y(), STAND.z(), cells.get(0));
        terrain.with(STAND.x(), STAND.y() - 1, STAND.z(), cells.get(1));
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int index = 2;
        for (int[] step : steps) {
            terrain.with(step[0], STAND.y(), step[1], cells.get(index++));
            for (int depth = 1; depth <= HazardDetector.DROP_DEPTH; depth++) {
                terrain.with(step[0], STAND.y() - depth, step[1], cells.get(index++));
            }
        }
        return terrain;
    }

    @Property
    void classify_AnyNeighbourhood_LiquidOnlyWhereCellIsLiquid(@ForAll("neighbourhoods") List<Terrain> cells) {
        Optional<HazardType> hazard = HazardDetector.classify(terrainOf(cells), STAND);

        assertEquals(
                cells.get(0) == Terrain.LAVA,
                hazard.equals(Optional.of(HazardType.LAVA)),
                "лава сообщается ровно тогда, когда в клетке лава, окружение " + cells);
        assertEquals(
                cells.get(0) == Terrain.WATER,
                hazard.equals(Optional.of(HazardType.WATER)),
                "вода сообщается ровно тогда, когда в клетке вода, окружение " + cells);
    }

    @Property
    void classify_AnyNeighbourhood_DropOnlyOverSolidFloorInAir(@ForAll("neighbourhoods") List<Terrain> cells) {
        Optional<HazardType> hazard = HazardDetector.classify(terrainOf(cells), STAND);

        if (hazard.equals(Optional.of(HazardType.DROP))) {
            assertTrue(
                    cells.get(0) == Terrain.AIR && cells.get(1) == Terrain.SOLID,
                    "обрыв возможен только в воздухе над твёрдым полом: иначе дух решит, что стоит на краю, "
                            + "а он в воде или в воздухе, окружение " + cells);
        }
    }
}
