package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.farming.api.CropKind;
import com.theminespirit.core.farming.api.HarvestMethod;
import com.theminespirit.core.mining.api.Cell;
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
                    "Столбовая культура " + name + " обязана собираться BREAK_COLUMN: иначе дух ломает нижний "
                            + "блок и столб не восстанавливается, урожай теряется. При смене таблицы культур "
                            + "проверь planner_TwoHighColumn_IsEligible и живой сбор тростника");
        }
    }

    @Test
    void isMature_Columnar_AnyAgeIsMature() {
        for (String name : List.of("sugar_cane", "bamboo", "cactus")) {
            CropKind kind = CropKind.parse(name);
            for (int age = 0; age <= kind.maxAge(); age++) {
                assertTrue(
                        new CropState(kind, age).isMature(),
                        "Столбовая культура " + name + " считается зрелой вне зависимости от age, высота решает: "
                                + "иначе недоросток столба не собирается вовсе и план поля встаёт. При смене "
                                + "CropState.isMature проверь planner_SingleColumn_IsIneligible");
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
                "Столб высотой 1 не цель: дух должен оставить недоросток, иначе сломает единственный блок и "
                        + "урожая не будет. При смене FarmPlanner.next проверь planner_TwoHighColumn_IsEligible");
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
                "Столб высотой 2 обязан быть целью: нижний остаётся, верх собирается, иначе ломается основание "
                        + "и столб пропадает. При смене FarmPlanner.next проверь planner_SingleColumn_IsIneligible");
        assertEquals(
                new BlockCoord(0, 0, 0),
                ((FarmPlanner.Next) result).target(),
                "Цель — земля под столбом: иначе дух бьёт по блоку не той высоты и столб не снимается. При "
                        + "смене цели в FarmPlanner.next проверь живой сбор тростника высотой 2");
    }

    @Test
    void eligible_Columnar_AnyHeightPassesButPlannerFilters() {
        CropKind cactus = CropKind.parse("cactus");
        FarmTile tile = new FarmTile(Ground.OTHER, Above.CROP, Optional.of(new CropState(cactus, 0)));
        assertTrue(
                FarmJob.HARVEST.eligible(tile, cactus, true),
                "HARVEST eligible для столба всегда true по клетке: высота проверяется планировщиком, иначе "
                        + "eligible отсечёт высокие столбы и сбор не начнётся. При смене FarmJob.HARVEST.eligible "
                        + "проверь planner_TwoHighColumn_IsEligible");
        assertFalse(
                FarmJob.HARVEST.eligible(new FarmTile(Ground.OTHER, Above.AIR, Optional.empty()), cactus, true),
                "Пустая клетка не harvestable: иначе дух бьёт по воздуху и тратит цикл фермы. При смене "
                        + "FarmJob.HARVEST.eligible проверь строку Above.AIR в этом тесте");
    }
}
