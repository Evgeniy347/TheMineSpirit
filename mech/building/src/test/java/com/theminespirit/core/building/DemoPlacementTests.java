package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.LoadedContent;
import com.theminespirit.core.reload.TableStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DemoPlacementTests {

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

    static PresetLibrary loadReal() {
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
