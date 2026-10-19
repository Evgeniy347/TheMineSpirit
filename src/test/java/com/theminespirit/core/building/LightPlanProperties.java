package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class LightPlanProperties {
    private static final int Y = 64;

    record Area(int radius, List<Integer> lights, List<Boolean> placeable) {}

    @Provide
    Arbitrary<Area> areas() {
        return Arbitraries.integers().between(2, 6).flatMap(radius -> {
            int side = 2 * radius + 1;
            Arbitrary<Integer> lights = Arbitraries.integers().between(0, 15);
            Arbitrary<Boolean> placeable = Arbitraries.of(true, true, true, false);
            return Combinators.combine(
                            lights.list().ofSize(side * side), placeable.list().ofSize(side * side))
                    .as((lightList, flags) -> new Area(radius, lightList, flags));
        });
    }

    @Provide
    Arbitrary<Area> openAreas() {
        return areas().map(area -> new Area(
                area.radius(), area.lights(), Collections.nCopies(area.lights().size(), true)));
    }

    @Property
    void plan_OpenArea_NoDarkCellRemains(@ForAll("openAreas") Area area) {
        List<LightCell> cells = cells(area);
        LightPlan.Plan plan = LightPlan.plan(0, 0, area.radius(), cells);

        for (LightCell cell : cells) {
            if (cell.light() > LightPlan.MOB_SPAWN_LIGHT) {
                continue;
            }
            boolean lit = plan.torches().stream().anyMatch(torch -> LightPlan.withinReach(torch, cell.coord()));
            assertTrue(
                    lit,
                    "После плана тёмная клетка " + cell.coord() + " осталась без света: "
                            + "мобы спавнятся при блочном свете 0, площадка небезопасна");
        }
        assertEquals(0, plan.darkRemaining(), "Счёт тёмных обязан обнулиться, когда все клетки размещаемы и достижимы");
    }

    @Property
    void plan_OpenArea_NoMoreTorchesThanSimpleGrid(@ForAll("openAreas") Area area) {
        List<LightCell> cells = cells(area);
        LightPlan.Plan plan = LightPlan.plan(0, 0, area.radius(), cells);

        int linesPerAxis = 2 * (area.radius() / LightPlan.GRID_STEP) + 1;
        int simpleGrid = linesPerAxis * linesPerAxis;
        assertTrue(
                plan.torches().size() <= simpleGrid,
                "Факелов " + plan.torches().size() + " больше, чем у простой сетки " + simpleGrid
                        + ": оптимизация не имеет права ухудшать наивную раскладку");
        assertTrue(
                plan.torches().size()
                        <= LightPlan.grid(0, 0, area.radius(), cells).size(),
                "План обязан быть подмножеством сетки-кандидата");
    }

    @Property
    void plan_BlockedArea_TorchesOnlyOnPlaceableCells(@ForAll("areas") Area area) {
        List<LightCell> cells = cells(area);
        LightPlan.Plan plan = LightPlan.plan(0, 0, area.radius(), cells);
        Map<BlockCoord, Boolean> placeable = new HashMap<>();
        for (LightCell cell : cells) {
            placeable.put(cell.coord(), cell.placeable());
        }

        for (BlockCoord torch : plan.torches()) {
            assertEquals(
                    Boolean.TRUE,
                    placeable.get(torch),
                    "Факел " + torch + " стоит на занятой или запрещённой клетке: чужую постройку и воду не трогаем");
        }
        assertTrue(
                plan.torches().size()
                        <= LightPlan.grid(0, 0, area.radius(), cells).size(),
                "Даже с запрещёнными клетками план не превышает сетку-кандидата");
    }

    @Property
    void plan_BlockedArea_DarkRemainingMatchesUncovered(@ForAll("areas") Area area) {
        List<LightCell> cells = cells(area);
        LightPlan.Plan plan = LightPlan.plan(0, 0, area.radius(), cells);

        int uncovered = 0;
        for (LightCell cell : cells) {
            if (cell.light() > LightPlan.MOB_SPAWN_LIGHT) {
                continue;
            }
            boolean lit = plan.torches().stream().anyMatch(torch -> LightPlan.withinReach(torch, cell.coord()));
            if (!lit) {
                uncovered++;
            }
        }
        assertEquals(
                uncovered,
                plan.darkRemaining(),
                "Счёт оставшихся тёмных клеток обязан совпасть с числом тёмных клеток вне досягаемости факелов");
    }

    @Property
    void plan_SameInput_TwiceSameResult(@ForAll("areas") Area area) {
        List<LightCell> cells = cells(area);

        assertEquals(
                LightPlan.plan(0, 0, area.radius(), cells),
                LightPlan.plan(0, 0, area.radius(), cells),
                "Тот же вход даёт тот же план: иначе исполнитель и бронь расходятся");
    }

    private static List<LightCell> cells(Area area) {
        List<LightCell> cells = new ArrayList<>();
        int index = 0;
        for (int x = -area.radius(); x <= area.radius(); x++) {
            for (int z = -area.radius(); z <= area.radius(); z++) {
                cells.add(new LightCell(
                        new BlockCoord(x, Y, z),
                        area.lights().get(index),
                        area.placeable().get(index)));
                index++;
            }
        }
        return cells;
    }
}
