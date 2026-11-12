package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.BitSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class WalkwaysTests {
    private static final ColumnMask EMPTY = new ColumnMask(0, 0, 41, 41, new BitSet());

    private static TownPlan plan(List<Placement> places, List<Integer> gates) {
        return new TownPlan(
                new BlockCoord(20, 70, 20),
                ColumnMask.rect(0, 0, 40, 40),
                new TownStreets(ColumnMask.rect(0, 20, 40, 20), EMPTY, EMPTY),
                places,
                new FenceLine(List.of(new ColumnMask.Cell(0, 20), new ColumnMask.Cell(40, 20)), gates),
                List.of());
    }

    @Test
    void of_HutFacingStreet_LinkFromDoorSideReachesStreet() {
        Placement hut = new Placement("hut", "Хижина", 1, 10, 10, 5, 5, Placement.Facing.SOUTH);

        List<ColumnMask.Cell> ways = Walkways.of(plan(List.of(hut), List.of(0)), 1);

        for (int z = 15; z <= 19; z++) {
            assertTrue(
                    ways.contains(new ColumnMask.Cell(12, z)),
                    "От середины стороны входа хижины тропа идёт до улицы, клетка z=" + z + ": " + ways);
        }
        assertTrue(ways.contains(new ColumnMask.Cell(20, 20)), "Улица плана — часть сети троп");
        assertTrue(ways.contains(new ColumnMask.Cell(0, 20)), "Калитка ограды — часть сети троп");
    }

    @Test
    void of_LaterStageBuilding_NotLinkedYet() {
        Placement workshop = new Placement("workshop", "Мастерская", 2, 25, 25, 5, 5, Placement.Facing.NORTH);

        List<ColumnMask.Cell> ways = Walkways.of(plan(List.of(workshop), List.of()), 1);

        assertTrue(!ways.contains(new ColumnMask.Cell(27, 24)), "Постройка этапа 2 на этапе 1 подхода не получает");
    }

    @Test
    void clearance_MoundOnWalkway_CutsTwoAboveFloorKeepsChest() {
        SettlementScanTests.Fake world = new SettlementScanTests.Fake();
        int level = SettlementScanTests.LEVEL;
        for (int y = level; y < level + 6; y++) {
            world.put(3, y, 0, "minecraft:dirt");
        }
        world.put(4, level, 0, "minecraft:chest");

        List<BlockCoord> cut = Walkways.clearance(
                world, "minecraft:chest"::equals, List.of(new ColumnMask.Cell(3, 0), new ColumnMask.Cell(4, 0)), level);

        assertTrue(
                cut.equals(List.of(new BlockCoord(3, level + 1, 0), new BlockCoord(3, level, 0))),
                "Над тропой срезаются 2 блока от пола сверху вниз, холм выше и сундук не трогаются: " + cut);
    }
}
