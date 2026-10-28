package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SchematicResources;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("slow")
class DemoPlacementSweepTests {
    private static final int[][] LOOKS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    @Test
    void compute_EveryBuildingAndLook_ChestAndSpawnFitOnPlatform() {
        PresetLibrary library = DemoPlacementTests.loadReal();
        for (DemoBuildings.Building building : DemoBuildings.all()) {
            for (DemoBuildings.Level level : building.levels()) {
                Schematic schematic = SchematicResources.load(level.scheme());
                Preset preset = library.byId(level.scheme()).orElseThrow();
                DemoPlacement.Door door = DemoPlacement.Door.of(preset);
                for (int[] look : LOOKS) {
                    DemoPlacement placement = DemoPlacement.compute(
                            new DemoPlacement.Anchor(0, 64, 0),
                            new DemoPlacement.Look(look[0], look[1]),
                            door,
                            new DemoPlacement.Size(schematic.sizeX(), schematic.sizeY(), schematic.sizeZ()),
                            null);
                    String where = building.id() + "/" + level.scheme() + " взгляд " + look[0] + "," + look[1];
                    BlockCoord front = new BlockCoord(0, 64, 0);
                    boolean onWall = door.normalDx() != 0 || door.normalDz() != 0;
                    assertTrue(
                            !onWall || !inSchema(placement, schematic, front),
                            where + ": клетка, куда смотрит игрок, обязана остаться перед крыльцом, вне схемы: "
                                    + "иначе ступенька встаёт под ноги игроку и вход не к нему лицом");
                    assertPlatform(placement, schematic, where);
                    assertChest(placement, schematic, front, where);
                    assertSpawn(placement, schematic, where);
                }
            }
        }
    }

    private static void assertPlatform(DemoPlacement placement, Schematic schematic, String where) {
        int pad = BuildTuning.DEMO_PLATFORM_PADDING;
        assertEquals(
                placement.sizeX() + 2 * pad,
                placement.platform().sideX(),
                where + ": площадка обязана быть шире основания на запас с двух сторон по X");
        assertEquals(
                placement.sizeZ() + 2 * pad,
                placement.platform().sideZ(),
                where + ": площадка обязана быть шире основания на запас с двух сторон по Z");
        assertEquals(
                schematic.sizeY() + pad,
                placement.platform().sideY(),
                where + ": площадка обязана быть выше схемы на запас, вниз не расширяясь");
    }

    private static void assertChest(DemoPlacement placement, Schematic schematic, BlockCoord front, String where) {
        BlockCoord chest = placement.chest();
        assertTrue(
                placement.platform().contains(chest),
                where + ": сундук обязан стоять на площадке, а не за её краем: " + chest);
        assertTrue(
                !inSchema(placement, schematic, chest),
                where + ": сундук обязан стоять вне клеток схемы, иначе он мешает постройке: " + chest);
        assertEquals(placement.origin().y(), chest.y(), where + ": сундук ставится на уровень пола площадки");
        assertNotEquals(front, chest, where + ": сундук не встаёт в клетку перед входом");
        assertTrue(
                chebyshev(chest, front) >= 2,
                where + ": сундук не встаёт и в соседнюю с входом клетку — дух обходит дом по площадке: " + chest);
        BlockCoord sign = placement.sign();
        assertTrue(
                sign != null && chebyshev(sign, chest) >= 1 && chebyshev(sign, chest) <= 2 && sign.y() == chest.y(),
                where + ": у сундука обязана стоять табличка с названием рядом, вдоль фасада (владелец 19.10): "
                        + sign);
        for (BlockCoord cell : chestGroupCells(placement)) {
            assertTrue(
                    placement.platform().contains(cell),
                    where + ": сундук, вторая половина и табличка обязаны стоять на площадке: " + cell);
            assertTrue(
                    !inSchema(placement, schematic, cell),
                    where + ": сундук, вторая половина и табличка обязаны стоять вне клеток схемы при любом "
                            + "повороте, иначе дух не очистит клетку ограды (владелец 21.10): " + cell);
        }
    }

    @Test
    void compute_EveryBuildingLookAndExplicitRotation_ChestGroupOutsideSchema() {
        PresetLibrary library = DemoPlacementTests.loadReal();
        for (DemoBuildings.Building building : DemoBuildings.all()) {
            for (DemoBuildings.Level level : building.levels()) {
                Schematic schematic = SchematicResources.load(level.scheme());
                Preset preset = library.byId(level.scheme()).orElseThrow();
                DemoPlacement.Door door = DemoPlacement.Door.of(preset);
                for (int[] look : LOOKS) {
                    for (BuildRotation rotation : BuildRotation.values()) {
                        DemoPlacement placement = DemoPlacement.compute(
                                new DemoPlacement.Anchor(0, 64, 0),
                                new DemoPlacement.Look(look[0], look[1]),
                                door,
                                new DemoPlacement.Size(schematic.sizeX(), schematic.sizeY(), schematic.sizeZ()),
                                rotation);
                        String where = building.id() + "/" + level.scheme() + " взгляд " + look[0] + "," + look[1]
                                + " поворот " + rotation.id();
                        assertChest(placement, schematic, new BlockCoord(0, 64, 0), where);
                        assertSpawn(placement, schematic, where);
                    }
                }
            }
        }
    }

    private static void assertSpawn(DemoPlacement placement, Schematic schematic, String where) {
        BlockCoord spawn = placement.spawn();
        assertTrue(
                placement.platform().contains(spawn),
                where + ": дух обязан появляться на площадке, а не за её краем: " + spawn);
        assertTrue(
                !inSchema(placement, schematic, spawn),
                where + ": дух не появляется в клетке схемы, иначе он задохнётся в блоках: " + spawn);
        for (BlockCoord cell : chestGroupCells(placement)) {
            assertTrue(
                    cell.x() != spawn.x() || cell.z() != spawn.z(),
                    where + ": дух не появляется в клетке сундука или таблички: " + cell);
        }
    }

    private static List<BlockCoord> chestGroupCells(DemoPlacement placement) {
        List<BlockCoord> cells = new ArrayList<>();
        cells.add(placement.chest());
        if (placement.chestPartner() != null) {
            cells.add(placement.chestPartner());
        }
        if (placement.sign() != null) {
            cells.add(placement.sign());
        }
        return cells;
    }

    private static int chebyshev(BlockCoord a, BlockCoord b) {
        return Math.max(Math.abs(a.x() - b.x()), Math.abs(a.z() - b.z()));
    }

    private static boolean inSchema(DemoPlacement placement, Schematic schematic, BlockCoord cell) {
        for (Schematic.PlacedEntry entry : schematic.entries(placement.origin(), placement.rotation())) {
            if (entry.pos().equals(cell)) {
                return true;
            }
        }
        return false;
    }
}
