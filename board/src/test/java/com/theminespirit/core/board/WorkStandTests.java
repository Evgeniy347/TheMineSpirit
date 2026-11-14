package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
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
    void near_NearestStandUnreachable_NextReachableChosen() {
        WorkStand.Ground yard = ground(0, Set.of());
        Optional<BlockCoord> stand =
                WorkStand.near(new BlockCoord(10, 0, 20), yard, feet -> !feet.equals(new BlockCoord(10, 0, 20)));

        assertEquals(
                Optional.of(new BlockCoord(9, 0, 20)),
                stand,
                "Стоянка в точке работы за преградой не годится: берётся ближайшая, до которой дух дойдёт пешком");
    }

    @Test
    void near_NoReachableCellWithinRadius_Empty() {
        WorkStand.Ground yard = ground(0, Set.of());
        assertEquals(
                Optional.empty(),
                WorkStand.near(new BlockCoord(10, 0, 20), yard, feet -> false),
                "Ни одной достижимой стоянки рядом — пусто: «путь есть» не выдумывается");
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
    void near_OccupiedNearestStand_NextFreeStand() {
        BlockCoord first = new BlockCoord(9, -52, 20);
        WorkStand.Ground flat = ground(-52, Set.of());

        Optional<BlockCoord> stand = WorkStand.near(new BlockCoord(10, -52, 20), flat, feet -> true, Set.of(first));

        assertNotEquals(
                Optional.of(first),
                stand,
                "Ближайшая стоянка занята другим духом — выбор уходит на следующую свободную: двое в одной "
                        + "клетке у сундука двора запирают друг друга, второй трётся без пути до stuck-провала "
                        + "(живой сервер 14.11: spirit-2 и spirit-4 оба на -181,69,-518)");
    }

    @Test
    void near_AllStandsOccupied_SharedSingleStand() {
        WorkStand.Ground flat = ground(-52, Set.of());
        Set<BlockCoord> all = new HashSet<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                all.add(new BlockCoord(10 + dx, -52, 20 + dz));
            }
        }

        Optional<BlockCoord> stand = WorkStand.near(new BlockCoord(10, -52, 20), flat, feet -> true, all);

        assertTrue(
                stand.isPresent(),
                "Свободных стоянок нет — духи встают в одну клетку (решение владельца 14.11): задание не "
                        + "проваливается и не ждёт, работа продолжается вплотную");
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
