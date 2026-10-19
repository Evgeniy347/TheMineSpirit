package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ColumnarTests {
    @Test
    void harvestMethod_Columnar_IsBreakColumn() {
        for (String name : List.of("sugar_cane", "bamboo", "cactus")) {
            CropKind kind = CropKind.parse(name);
            assertEquals(
                    HarvestMethod.BREAK_COLUMN,
                    kind.harvestMethod(),
                    "Столбовая культура " + name + " обязана собираться BREAK_COLUMN: иначе дух ломает нижний блок");
        }
    }

    @Test
    void isMature_Columnar_AnyAgeIsMature() {
        for (String name : List.of("sugar_cane", "bamboo", "cactus")) {
            CropKind kind = CropKind.parse(name);
            for (int age = 0; age <= kind.maxAge(); age++) {
                assertTrue(
                        new CropState(kind, age).isMature(),
                        "Столбовая культура " + name + " считается зрелой вне зависимости от age, высота решает");
            }
        }
    }

    @Test
    void planner_SingleColumn_IsIneligible() {
        CropKind cane = CropKind.parse("sugar_cane");
        FarmTile tile = new FarmTile(Ground.OTHER, Above.CROP, Optional.of(new CropState(cane, 0)));
        MapFarmView view = new MapFarmView().set(0, 0, 0, tile).height(0, 0, 0, 1);
        var result = FarmPlanner.next(
                FarmJob.HARVEST,
                cane,
                new BlockCoord(0, 1, 0),
                List.of(new BlockCoord(0, 0, 0)),
                view,
                coord -> coord.y() == 0 ? Cell.SOLID : Cell.PASSABLE,
                Hydration.of(Set.of()));
        assertTrue(
                result instanceof FarmPlanner.Exhausted,
                "Столб высотой 1 не цель: дух должен оставить недоросток, а выбрано " + result);
    }

    @Test
    void planner_TwoHighColumn_IsEligible() {
        CropKind cane = CropKind.parse("sugar_cane");
        FarmTile tile = new FarmTile(Ground.OTHER, Above.CROP, Optional.of(new CropState(cane, 0)));
        MapFarmView view = new MapFarmView().set(0, 0, 0, tile).height(0, 0, 0, 2);
        var result = FarmPlanner.next(
                FarmJob.HARVEST,
                cane,
                new BlockCoord(0, 1, 0),
                List.of(new BlockCoord(0, 0, 0)),
                view,
                coord -> coord.y() == 0 ? Cell.SOLID : Cell.PASSABLE,
                Hydration.of(Set.of()));
        assertTrue(
                result instanceof FarmPlanner.Next,
                "Столб высотой 2 обязан быть целью: нижний остаётся, верх собирается");
        assertEquals(new BlockCoord(0, 0, 0), ((FarmPlanner.Next) result).target(), "цель — земля под столбом");
    }

    @Test
    void eligible_Columnar_AnyHeightPassesButPlannerFilters() {
        CropKind cactus = CropKind.parse("cactus");
        FarmTile tile = new FarmTile(Ground.OTHER, Above.CROP, Optional.of(new CropState(cactus, 0)));
        assertTrue(
                FarmJob.HARVEST.eligible(tile, cactus, true),
                "HARVEST eligible для столба всегда true по клетке: высота проверяется планировщиком");
        assertFalse(
                FarmJob.HARVEST.eligible(new FarmTile(Ground.OTHER, Above.AIR, Optional.empty()), cactus, true),
                "Пустая клетка не harvestable");
    }
}
