package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.LoadedContent;
import com.theminespirit.core.reload.TableStatus;
import com.theminespirit.neoforge.SchematicResources;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DemoPlacementTests {
    private static final int[][] LOOKS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    @Test
    void compute_LookSouth_TurnsEntryToPlayerAndPlacesChestAside() {
        DemoPlacement placement = DemoPlacement.compute(
                new DemoPlacement.Anchor(10, 64, 10),
                new DemoPlacement.Look(0, 1),
                2,
                0,
                new DemoPlacement.Size(5, 3, 5),
                null);

        assertEquals(
                BuildRotation.NONE,
                placement.rotation(),
                "Схема с входом на северной стороне и игроком, смотрящим на юг, не поворачивается: "
                        + "вход и так смотрит на игрока");
        assertEquals(
                new BlockCoord(8, 64, 11),
                placement.origin(),
                "Начало постройки обязано ставить клетку входа на одну клетку за якорем по взгляду игрока");
        assertEquals(
                new BlockCoord(8, 64, 10),
                placement.chest(),
                "Сундук ставится в стороне от входа, на два блока левее: перед дверью ему не место, "
                        + "а в клетке схемы он мешает стройке");
        assertEquals(
                new BlockCoord(10, 64, 10),
                placement.spawn(),
                "Дух появляется в клетке перед входом, на площадке: оттуда он видит дверь и не стоит в схеме");
        assertEquals(5, placement.sizeX(), "Без поворота сторона постройки совпадает со схемой");
        assertEquals(5, placement.sizeZ(), "Без поворота сторона постройки совпадает со схемой");
    }

    @Test
    void compute_Platform_IsFootprintPlusPaddingSidesAndUp() {
        DemoPlacement placement = DemoPlacement.compute(
                new DemoPlacement.Anchor(10, 64, 10),
                new DemoPlacement.Look(0, 1),
                2,
                0,
                new DemoPlacement.Size(5, 3, 5),
                null);
        int pad = BuildTuning.DEMO_PLATFORM_PADDING;

        assertEquals(
                5,
                pad,
                "Запас площадки демо-стройки — число данных, равное 5: другое значение меняет и очистку, "
                        + "и обход дома духом");
        assertEquals(15, placement.platform().sideX(), "Площадка шире схемы на запас с обеих сторон по X: 5 + 10 = 15");
        assertEquals(15, placement.platform().sideZ(), "Площадка шире схемы на запас с обеих сторон по Z: 5 + 10 = 15");
        assertEquals(8, placement.platform().sideY(), "Площадка выше схемы на запас (вниз не расширяется): 3 + 5 = 8");
        assertEquals(
                64,
                placement.platform().minY(),
                "Низ площадки совпадает с низом схемы: ниже копать не нужно, земля выравнивается отдельно");
        assertEquals(
                64 + 3 - 1 + pad,
                placement.platform().maxY(),
                "Верх площадки — верх схемы плюс запас: дух поднимается над крышей без упора в землю");
        assertEquals(
                64 - 1,
                placement.floorBox().minY(),
                "Пол выравнивается слоем под низом схемы: иначе под кольцом вокруг постройки останется яма");
    }

    @Test
    void compute_LookEast_TurnsEntryToPlayer() {
        DemoPlacement placement = DemoPlacement.compute(
                new DemoPlacement.Anchor(10, 64, 10),
                new DemoPlacement.Look(1, 0),
                2,
                0,
                new DemoPlacement.Size(5, 3, 5),
                null);

        assertEquals(
                BuildRotation.COUNTERCLOCKWISE_90,
                placement.rotation(),
                "Игрок смотрит на восток — вход на северной стороне обязан повернуться на запад, к игроку");
        BuildRotation.Cell entry = placement.rotation().rotate(2, 0, 5, 5);
        assertEquals(
                11,
                placement.origin().x() + entry.x(),
                "Клетка входа обязана встать на одну клетку за сундуком по взгляду игрока (ось X)");
        assertEquals(
                10,
                placement.origin().z() + entry.z(),
                "Клетка входа обязана встать на одну клетку за сундуком по взгляду игрока (ось Z)");
    }

    @Test
    void compute_ExplicitRotation_OverridesLook() {
        DemoPlacement placement = DemoPlacement.compute(
                new DemoPlacement.Anchor(10, 64, 10),
                new DemoPlacement.Look(0, 1),
                2,
                0,
                new DemoPlacement.Size(5, 3, 5),
                BuildRotation.CLOCKWISE_180);

        assertEquals(
                BuildRotation.CLOCKWISE_180,
                placement.rotation(),
                "Явный поворот обязан перебивать вывод из взгляда игрока: иначе параметр поворота не работает");
        BuildRotation.Cell entry = BuildRotation.CLOCKWISE_180.rotate(2, 0, 5, 5);
        assertEquals(
                new BlockCoord(10 + 0 - entry.x(), 64, 10 + 1 - entry.z()),
                placement.origin(),
                "Начало постройки обязано считаться с явным поворотом");
    }

    @Test
    void compute_EntryInDepth_KeepsNoRotation() {
        DemoPlacement placement = DemoPlacement.compute(
                new DemoPlacement.Anchor(0, 64, 0),
                new DemoPlacement.Look(1, 0),
                1,
                1,
                new DemoPlacement.Size(3, 2, 3),
                null);

        assertEquals(
                BuildRotation.NONE,
                placement.rotation(),
                "Вход внутри схемы (колодец) не задаёт сторону: поворот не выводится из взгляда, постройка "
                        + "остаётся как в схеме");
    }

    @Test
    void compute_EntryOnSouthEdge_LooksNorthToPlayer() {
        DemoPlacement placement = DemoPlacement.compute(
                new DemoPlacement.Anchor(0, 64, 0),
                new DemoPlacement.Look(0, 1),
                4,
                8,
                new DemoPlacement.Size(9, 4, 9),
                null);

        assertEquals(
                BuildRotation.CLOCKWISE_180,
                placement.rotation(),
                "Вход на южной стороне (чаровальня) при взгляде на юг обязан перевернуть схему, чтобы вход "
                        + "смотрел на игрока");
    }

    @Test
    void door_FamilyHouseWithPorch_FacesPlayerAndStepIsInFrontOfDoor() {
        Preset family = loadReal().byId("family_house").orElseThrow();
        DemoPlacement.Door door = DemoPlacement.Door.of(family);

        DemoPlacement placement = DemoPlacement.compute(
                new DemoPlacement.Anchor(10, 64, 10),
                new DemoPlacement.Look(0, 1),
                door,
                new DemoPlacement.Size(family.sizeX(), family.sizeY(), family.sizeZ()),
                null);

        assertEquals(
                new DemoPlacement.Door(4, 1, 0, -1, 1),
                door,
                "Дверь дома семьи — на северной стене тела, перед ней крыльцо в одну клетку со ступенькой");
        assertEquals(
                BuildRotation.NONE,
                placement.rotation(),
                "Вход на севере тела при взгляде на юг уже смотрит на игрока: поворачивать не нужно");
        assertEquals(
                new BlockCoord(6, 64, 11),
                placement.origin(),
                "Якорь — клетка перед крыльцом: ступенька встаёт на клетку дальше по взгляду, дверь — ещё на одну");
    }

    @Test
    void compute_EveryBuildingAndLook_ChestAndSpawnFitOnPlatform() {
        PresetLibrary library = loadReal();
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

    // Замечание владельца 21.10: табличка у сундука попадала в клетку ограды загона при повороте. Вся группа
    // «сундук + половина + табличка» обязана вращаться вместе с постройкой — проверка на всех поворотах явно.
    @Test
    void compute_EveryBuildingLookAndExplicitRotation_ChestGroupOutsideSchema() {
        PresetLibrary library = loadReal();
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

    private static PresetLibrary loadReal() {
        List<LoadedContent> contents = new ArrayList<>();
        String indexJson = JsonResource.readClasspath(PresetLibrary.SCHEMATICS_ROOT + PresetLibrary.INDEX_ID + ".json");
        contents.add(new LoadedContent(
                indexJson, PresetLibrary.SCHEMATICS_ROOT + PresetLibrary.INDEX_ID + ".json", TableStatus.Source.MOD));
        for (String path :
                PresetLibrary.bodyPaths(indexJson, PresetLibrary.SCHEMATICS_ROOT + PresetLibrary.INDEX_ID + ".json")) {
            contents.add(new LoadedContent(JsonResource.readClasspath(path), path, TableStatus.Source.MOD));
        }
        return PresetLibrary.parse(contents);
    }
}
