package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkStandTests {
    @Test
    void near_PointAboveCutGround_StandAtCurrentFeet() {
        WorkStand.Ground cut = ground(-52, Set.of());

        Optional<BlockCoord> stand = WorkStand.near(new BlockCoord(10, -48, 20), cut);

        assertEquals(
                Optional.of(new BlockCoord(10, -52, 20)),
                stand,
                "Точка задания с высотой до срезки (-48) ведёт духа в воздух — «стоянка за преградой»; высота "
                        + "стоянки — по текущему рельефу (-52)");
    }

    @Test
    void near_PointOccupiedByTable_NearestFreeNeighbour() {
        WorkStand.Ground yard = ground(-52, Set.of(new BlockCoord(10, -52, 20)));

        Optional<BlockCoord> stand = WorkStand.near(new BlockCoord(10, -52, 20), yard);

        assertEquals(
                Optional.of(new BlockCoord(9, -52, 20)),
                stand,
                "Верстак стоит в самой точке: дух не встанет в блок, крафт ведёт на ближайшую свободную клетку рядом");
    }

    @Test
    void near_NoFreeCellWithinRadius_Empty() {
        WorkStand.Ground walled = new WorkStand.Ground() {
            @Override
            public int feet(int x, int z) {
                return 0;
            }

            @Override
            public boolean free(BlockCoord feet) {
                return false;
            }
        };

        assertEquals(
                Optional.empty(),
                WorkStand.near(new BlockCoord(0, 0, 0), walled),
                "Нет свободной клетки в радиусе " + WorkStand.RADIUS + " — стоянки нет, задание не ставится в стену");
    }

    private static WorkStand.Ground ground(int feet, Set<BlockCoord> occupied) {
        return new WorkStand.Ground() {
            @Override
            public int feet(int x, int z) {
                return feet;
            }

            @Override
            public boolean free(BlockCoord at) {
                return !occupied.contains(at);
            }
        };
    }

    @Test
    void near_UncutPillarAtPoint_NeighbourOnPlotLevel() {
        BlockCoord pillar = new BlockCoord(10, -48, 20);
        WorkStand.Ground hill = new WorkStand.Ground() {
            @Override
            public int feet(int x, int z) {
                return x == pillar.x() && z == pillar.z() ? pillar.y() : -52;
            }

            @Override
            public boolean free(BlockCoord feet) {
                return true;
            }
        };

        Optional<BlockCoord> stand = WorkStand.near(new BlockCoord(10, -52, 20), hill);

        assertEquals(
                Optional.of(new BlockCoord(9, -52, 20)),
                stand,
                "Несрезанный столб в точке работы (ноги -48 при уровне площадки -52): на него не забраться — "
                        + "стоянка на уровне точки (±1), а не на вершине столба");
    }
}
