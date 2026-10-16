package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.LoadedContent;
import com.theminespirit.core.reload.TableStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PresetLibraryTests {
    private static final String INDEX_PATH = PresetLibrary.SCHEMATICS_ROOT + PresetLibrary.INDEX_ID + ".json";

    private static PresetLibrary loadReal() {
        List<LoadedContent> contents = new ArrayList<>();
        String indexJson = JsonResource.readClasspath(INDEX_PATH);
        contents.add(new LoadedContent(indexJson, INDEX_PATH, TableStatus.Source.MOD));
        for (String path : PresetLibrary.bodyPaths(indexJson, INDEX_PATH)) {
            contents.add(new LoadedContent(JsonResource.readClasspath(path), path, TableStatus.Source.MOD));
        }
        return PresetLibrary.parse(contents);
    }

    private static String body(String jsonSize, String blocks) {
        return "{\"version\":1,\"size\":[" + jsonSize + "],\"blocks\":[" + blocks + "]}";
    }

    private static LoadedContent indexWith(String presetFields) {
        return new LoadedContent("{\"version\":1,\"presets\":[" + presetFields + "]}", "тестовый индекс",
                TableStatus.Source.BUILTIN);
    }

    private static LoadedContent bodyContent(String bodyJson) {
        return new LoadedContent(bodyJson, "тестовое тело", TableStatus.Source.BUILTIN);
    }

    @Test
    void parse_RealIndex_LoadsAllEightPresets() {
        PresetLibrary library = loadReal();

        assertEquals(8, library.size(),
                "Библиотека этапа 0–1 обязана прочитать все восемь пресетов: без них стратегу не из чего "
                        + "составлять планировку поселения");
        assertEquals(List.of("palisade_section", "torch_post", "storage_hut", "family_house", "stone_house",
                "well", "pen_7x7", "mine_entrance_v1"), library.ids(),
                "Состав пресетов обязан совпадать с индексом: пропажа пресета ломает застройку этапа");
    }

    @Test
    void parse_EachRealPreset_PassesLibraryChecks() {
        PresetLibrary library = loadReal();

        for (Preset preset : library.all()) {
            assertEquals(preset.materials(), PresetLibrary.countMaterials(preset.schematic()),
                    "У пресета '" + preset.id() + "' материалы обязаны считаться из блоков, а не задаваться вручную");
            assertTrue(preset.entry().x() >= 0 && preset.entry().x() < preset.sizeX()
                            && preset.entry().y() >= 0 && preset.entry().y() < preset.sizeY()
                            && preset.entry().z() >= 0 && preset.entry().z() < preset.sizeZ(),
                    "У пресета '" + preset.id() + "' точка входа обязана лежать внутри габаритов, иначе в постройку "
                            + "не войти: " + preset.entry());
            assertTrue(preset.stage() >= Preset.STAGE_MIN && preset.stage() <= Preset.STAGE_MAX,
                    "У пресета '" + preset.id() + "' этап развития обязан быть 0..4, фактически " + preset.stage());
        }
    }

    @Test
    void parse_DeclaredSizeDiffersFromBody_IsRejected() {
        LoadedContent index = indexWith("{\"id\":\"hut\",\"file\":\"hut\",\"name\":\"Хижина\",\"stage\":0,"
                + "\"type\":\"housing\",\"size\":[5,4,5],\"entry\":[2,1,0]}");
        LoadedContent body = bodyContent(body("2,1,1", "{\"pos\":[0,0,0],\"block\":\"minecraft:oak_planks\"},"
                + "{\"pos\":[1,0,0],\"block\":\"minecraft:oak_planks\"}"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PresetLibrary.parse(List.of(index, body)),
                "Метаданные, разошедшиеся с содержимым (габариты 5×4×5 против тела 2×1×1), обязаны отвергаться: "
                        + "иначе пресет ставится по чужим габаритам");

        assertTrue(error.getMessage().contains("габариты"),
                "Ошибка обязана называть причину — расхождение габаритов: иначе непонятно, что чинить; фактически: "
                        + error.getMessage());
    }

    @Test
    void parse_EntryInsideSolidBlock_IsRejected() {
        LoadedContent index = indexWith("{\"id\":\"post\",\"file\":\"post\",\"name\":\"Столб\",\"stage\":0,"
                + "\"type\":\"infrastructure\",\"size\":[1,1,1],\"entry\":[0,0,0]}");
        LoadedContent body = bodyContent(body("1,1,1", "{\"pos\":[0,0,0],\"block\":\"minecraft:oak_planks\"}"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> PresetLibrary.parse(List.of(index, body)),
                "Точка входа, занятая сплошным блоком, обязана отвергаться: сквозь стену не войти");

        assertTrue(error.getMessage().contains("вход"),
                "Ошибка обязана называть точку входа: без неё непонятно, где проём; фактически: " + error.getMessage());
    }

    @Test
    void parse_EntryOutsideSize_IsRejected() {
        LoadedContent index = indexWith("{\"id\":\"post\",\"file\":\"post\",\"name\":\"Столб\",\"stage\":0,"
                + "\"type\":\"infrastructure\",\"size\":[1,1,1],\"entry\":[2,0,0]}");
        LoadedContent body = bodyContent(body("1,1,1", "{\"pos\":[0,0,0],\"block\":\"minecraft:oak_planks\"}"));

        assertThrows(IllegalStateException.class, () -> PresetLibrary.parse(List.of(index, body)),
                "Точка входа вне габаритов обязана отвергаться: через клетку за постройкой в неё не войти");
    }

    @Test
    void materials_BodyGainsOneBlock_CountsFollowBlocks() {
        LoadedContent index = indexWith("{\"id\":\"floor\",\"file\":\"floor\",\"name\":\"Пол\",\"stage\":0,"
                + "\"type\":\"infrastructure\",\"size\":[3,1,1],\"entry\":[2,0,0]}");
        LoadedContent one = bodyContent(body("3,1,1", "{\"pos\":[0,0,0],\"block\":\"minecraft:cobblestone\"}"));
        LoadedContent two = bodyContent(body("3,1,1", "{\"pos\":[0,0,0],\"block\":\"minecraft:cobblestone\"},"
                + "{\"pos\":[1,0,0],\"block\":\"minecraft:cobblestone\"}"));

        Preset single = PresetLibrary.parse(List.of(index, one)).byId("floor").orElseThrow();
        Preset doubled = PresetLibrary.parse(List.of(index, two)).byId("floor").orElseThrow();

        assertEquals(1, single.materials().get("minecraft:cobblestone"),
                "Один блок в теле обязан дать один материал: число считается из блоков, а не пишется вручную");
        assertEquals(2, doubled.materials().get("minecraft:cobblestone"),
                "Два блока в теле обязаны дать два материала: ручное число, совпавшее случайно, развалилось бы "
                        + "на подлоге с лишним блоком");
    }

    @Test
    void byType_Defense_ReturnsPalisadeSection() {
        PresetLibrary library = loadReal();

        assertEquals(List.of("palisade_section"), library.byType(BuildingType.DEFENSE).stream()
                        .map(Preset::id).toList(),
                "Поиск по типу «оборона» обязан находить палисад-секцию: по нему стратег выбирает ограждение");
    }

    @Test
    void byStage_StageZero_ReturnsCampStructures() {
        PresetLibrary library = loadReal();

        assertEquals(List.of("palisade_section", "torch_post", "mine_entrance_v1"),
                library.byStage(0).stream().map(Preset::id).toList(),
                "Этап 0 «лагерь выживания» — палисад, факелы и вход в шахту: по этапу стратег решает, "
                        + "что строить сейчас");
    }

    @Test
    void byIdAndTypeAndStage_StorageHut_MatchesStorageAtStageOne() {
        PresetLibrary library = loadReal();

        assertEquals(List.of("storage_hut"),
                library.byTypeAndStage(BuildingType.STORAGE, 1).stream().map(Preset::id).toList(),
                "Склад-хижина обязана находиться одновременно по типу «склад» и этапу 1");
    }

    @Test
    void countMaterials_DuplicateBlockId_SumsCounts() {
        Schematic schematic = SchematicParser.parse(body("3,1,1",
                "{\"pos\":[0,0,0],\"block\":\"minecraft:cobblestone\"},"
                        + "{\"pos\":[1,0,0],\"block\":\"minecraft:cobblestone\"},"
                        + "{\"pos\":[2,0,0],\"block\":\"minecraft:oak_planks\"}"), "тест");
        Map<String, Integer> materials = PresetLibrary.countMaterials(schematic);

        assertEquals(2, materials.get("minecraft:cobblestone"),
                "Повтор одного блока обязан суммироваться: иначе нехватка материала на стройке");
        assertEquals(1, materials.get("minecraft:oak_planks"),
                "Второй вид блока обязан идти отдельной записью с числом 1");
    }

    @Test
    void bodyPaths_RealIndex_ReturnsOnePathPerPreset() {
        String indexJson = JsonResource.readClasspath(INDEX_PATH);

        List<String> paths = PresetLibrary.bodyPaths(indexJson, INDEX_PATH);

        assertEquals(8, paths.size(),
                "Индекс обязан назвать по телу на каждый из восьми пресетов: пропуск тела оставил бы пресет без схемы");
        assertEquals(PresetLibrary.SCHEMATICS_ROOT + "storage_hut.json", paths.get(2),
                "Порядок тел обязан совпадать с порядком пресетов в индексе: иначе метаданные склеятся с чужой схемой");
    }

    @Test
    void entry_WellWaterCenter_IsAboveWater() {
        Preset well = loadReal().byId("well").orElseThrow();

        assertEquals(new BlockCoord(1, 1, 1), well.entry(),
                "У колодца вход — клетка над водой: спуск к воде, а не в сплошной блок");
    }
}