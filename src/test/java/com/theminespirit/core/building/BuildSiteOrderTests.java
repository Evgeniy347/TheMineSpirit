package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.BuildSiteOrder.StandKind;
import com.theminespirit.core.building.BuildSiteOrder.Step;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BuildSiteOrderTests {
    /** Полый короб: периметр стен до maxY-1, крыша — весь верхний слой; проём — дыра в стене на нижнем слое. */
    private static List<BlockCoord> hollowBox(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
        int maxX = minX + sizeX - 1;
        int maxY = minY + sizeY - 1;
        int maxZ = minZ + sizeZ - 1;
        List<BlockCoord> cells = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean boundary = x == minX || x == maxX || z == minZ || z == maxZ;
                    boolean roof = y == maxY;
                    if (roof || (boundary && y < maxY)) {
                        cells.add(new BlockCoord(x, y, z));
                    }
                }
            }
        }
        return cells;
    }

    private static BlockCoord doorwayOf(int minX, int minY, int minZ, int sizeX) {
        return new BlockCoord(minX + sizeX / 2, minY, minZ);
    }

    @Test
    void plan_ClosedBoxWithDoorway_EveryCellOnceAndStandReachable() {
        List<BlockCoord> cells = hollowBox(0, 0, 0, 5, 3, 5);
        BlockCoord doorway = doorwayOf(0, 0, 0, 5);

        List<Step> plan = BuildSiteOrder.plan(cells, doorway);

        Set<BlockCoord> cellsInPlan = new HashSet<>();
        for (Step step : plan) {
            cellsInPlan.add(step.cell());
        }
        Set<BlockCoord> expected = new HashSet<>(cells);
        expected.add(doorway);
        assertEquals(expected, cellsInPlan,
                "План обязан содержать каждую клетку ровно один раз: потеря даёт дыру, дубль — двойной расход");
        assertEquals(expected.size(), plan.size(),
                "Число шагов обязано равняться числу клеток: иначе часть коробки не строится");
    }

    @Test
    void plan_AnyStep_StandIsNotCellToPlace() {
        List<BlockCoord> cells = hollowBox(0, 0, 0, 5, 3, 5);
        BlockCoord doorway = doorwayOf(0, 0, 0, 5);

        List<Step> plan = BuildSiteOrder.plan(cells, doorway);

        for (Step step : plan) {
            assertFalse(step.stand().equals(step.cell()),
                    "Стоянка не может совпасть с клеткой постановки: дух окажется замурован в собственном блоке");
        }
    }

    @Test
    void plan_AnyStep_StandIsNeverFutureTarget() {
        List<BlockCoord> cells = hollowBox(0, 0, 0, 5, 3, 5);
        BlockCoord doorway = doorwayOf(0, 0, 0, 5);

        List<Step> plan = BuildSiteOrder.plan(cells, doorway);

        Set<BlockCoord> placed = new HashSet<>();
        for (Step step : plan) {
            boolean futureTarget = cells.contains(step.stand()) && !placed.contains(step.stand());
            assertFalse(futureTarget,
                    "Стоянка не может быть ещё не поставленной клеткой: дух стоял бы там, куда обязан поставить блок");
            placed.add(step.cell());
        }
    }

    @Test
    void plan_ClosedBoxWithRoof_RoofComesAfterAllWalls() {
        List<BlockCoord> cells = hollowBox(0, 0, 0, 5, 3, 5);
        BlockCoord doorway = doorwayOf(0, 0, 0, 5);

        List<Step> plan = BuildSiteOrder.plan(cells, doorway);

        int lastWall = -1;
        int firstRoof = Integer.MAX_VALUE;
        for (int index = 0; index < plan.size(); index++) {
            Step step = plan.get(index);
            if (step.cell().equals(doorway)) {
                continue;
            }
            if (step.cell().y() == 2) {
                firstRoof = Math.min(firstRoof, index);
            } else {
                lastWall = Math.max(lastWall, index);
            }
        }
        assertTrue(lastWall < firstRoof,
                "Крыша обязана ставиться после всех стен: крыша без стен строителю не на что опереться");
    }

    @Test
    void plan_Doorway_PlacedLast() {
        List<BlockCoord> cells = hollowBox(0, 0, 0, 5, 3, 5);
        BlockCoord doorway = doorwayOf(0, 0, 0, 5);

        List<Step> plan = BuildSiteOrder.plan(cells, doorway);

        assertEquals(doorway, plan.get(plan.size() - 1).cell(),
                "Блок проёма обязан ставиться последним: иначе дух замурует себя внутри постройки");
    }

    @Test
    void plan_OutsideWall_StandIsOutsideFootprint() {
        List<BlockCoord> cells = hollowBox(0, 0, 0, 5, 3, 5);
        BlockCoord doorway = doorwayOf(0, 0, 0, 5);

        List<Step> plan = BuildSiteOrder.plan(cells, doorway);

        for (Step step : plan) {
            if (step.kind() == StandKind.OUTSIDE && !step.cell().equals(doorway)) {
                BlockCoord stand = step.stand();
                boolean outside = stand.x() < 0 || stand.x() > 4 || stand.z() < 0 || stand.z() > 4;
                assertTrue(outside,
                        "Внешняя стоянка обязана быть вне следа постройки: иначе дух стоит в стене, которую ставит");
            }
        }
    }

    @Test
    void plan_DoorwayNotInCells_IsNotPlaced() {
        List<BlockCoord> cells = hollowBox(0, 0, 0, 5, 3, 5);
        BlockCoord doorway = doorwayOf(0, 0, 0, 5);

        List<Step> plan = BuildSiteOrder.plan(cells, doorway);

        long doorwaySteps = plan.stream().filter(step -> step.cell().equals(doorway)).count();
        assertEquals(1, doorwaySteps,
                "Проём, отсутствующий в схеме, ставится один раз последним шагом: дух выходит и закрывает проход");
    }

    @Test
    void plan_InvalidInput_Throws() {
        assertThrows(NullPointerException.class, () -> BuildSiteOrder.plan(null, null),
                "План без клеток обязан отвергаться: без него неизвестно, что строить");
    }

    @Test
    void plan_EmptyInput_GivesEmpty() {
        assertEquals(List.of(), BuildSiteOrder.plan(List.of(), null),
                "Пустой вход обязан давать пустой план: иначе дух начинает стройку из ничего");
    }
}