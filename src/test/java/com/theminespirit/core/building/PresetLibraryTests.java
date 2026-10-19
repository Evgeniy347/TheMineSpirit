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
import java.util.Optional;
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

    private static String body(String header, String... rows) {
        StringBuilder text = new StringBuilder(header)
                .append("legend:\n  'C': cobblestone\n  '#': oak_planks\n")
                .append("  'D': oak_door[facing=north]\nlayers:\n");
        for (int y = 0; y < rows.length; y++) {
            text.append("  - y").append(y).append(": |\n");
            for (String row : rows[y].split("/")) {
                text.append("      ").append(row).append('\n');
            }
        }
        return text.toString();
    }

    private static LoadedContent indexWith(String... ids) {
        return new LoadedContent(
                "{\"version\":2,\"presets\":[\"" + String.join("\",\"", ids) + "\"]}",
                "тестовый индекс",
                TableStatus.Source.BUILTIN);
    }

    private static LoadedContent bodyContent(String text) {
        return new LoadedContent(text, "тестовое тело", TableStatus.Source.BUILTIN);
    }

    @Test
    void parse_RealIndex_LoadsAllSixteenPresets() {
        PresetLibrary library = loadReal();

        assertEquals(
                16,
                library.size(),
                "Библиотека обязана прочитать все шестнадцать пресетов, включая два уровня чаровальни, "
                        + "четыре уровня склада и загоны трёх уровней: без них стратегу не из чего "
                        + "составлять планировку поселения");
        assertEquals(
                List.of(
                        "palisade_section",
                        "torch_post",
                        "storage_hut",
                        "family_house",
                        "stone_house",
                        "well",
                        "pen_7x7",
                        "pen_4x5",
                        "pen_9x9",
                        "mine_entrance_v1",
                        "enchant_room_v1",
                        "enchant_room_v2",
                        "storage_v1",
                        "storage_v2",
                        "storage_v3",
                        "storage_v4"),
                library.ids(),
                "Состав пресетов обязан совпадать с индексом: пропажа пресета ломает застройку этапа");
    }

    @Test
    void parse_EachRealPreset_PassesLibraryChecks() {
        PresetLibrary library = loadReal();

        for (Preset preset : library.all()) {
            assertEquals(
                    preset.materials(),
                    PresetLibrary.countMaterials(preset.schematic()),
                    "У пресета '" + preset.id() + "' материалы обязаны считаться из блоков, а не задаваться вручную");
            assertTrue(
                    preset.entry().x() >= 0
                            && preset.entry().x() < preset.sizeX()
                            && preset.entry().y() >= 0
                            && preset.entry().y() < preset.sizeY()
                            && preset.entry().z() >= 0
                            && preset.entry().z() < preset.sizeZ(),
                    "У пресета '" + preset.id() + "' точка входа обязана лежать внутри габаритов, иначе в постройку "
                            + "не войти: " + preset.entry());
            assertTrue(
                    preset.stage() >= Preset.STAGE_MIN && preset.stage() <= Preset.STAGE_MAX,
                    "У пресета '" + preset.id() + "' этап развития обязан быть 0..4, фактически " + preset.stage());
        }
    }

    @Test
    void parse_PresetWithoutName_IsRejected() {
        LoadedContent body = bodyContent(body("type: housing\nstage: 0\nsize: 2x1x1\nentrance: 0,0,0\n", ".C"));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> PresetLibrary.parse(List.of(indexWith("hut"), body)),
                "Пресет без названия обязан отвергаться: /spirit presets и стратег показывают постройку по имени");

        assertTrue(
                error.getMessage().contains("name"),
                "Ошибка обязана называть недостающий ключ name; фактически: " + error.getMessage());
    }

    @Test
    void parse_EntryInsideSolidBlock_IsRejected() {
        LoadedContent body = bodyContent(
                body("name: Столб\ntype: infrastructure\nstage: 0\nsize: 1x1x1\n" + "entrance: 0,0,0\n", "#"));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> PresetLibrary.parse(List.of(indexWith("post"), body)),
                "Точка входа, занятая сплошным блоком, обязана отвергаться: сквозь стену не войти");

        assertTrue(
                error.getMessage().contains("вход"),
                "Ошибка обязана называть точку входа: без неё непонятно, где проём; фактически: " + error.getMessage());
    }

    @Test
    void parse_EntryOutsideSize_IsRejected() {
        LoadedContent body = bodyContent(
                body("name: Столб\ntype: infrastructure\nstage: 0\nsize: 1x1x1\n" + "entrance: 2,0,0\n", "#"));

        assertThrows(
                IllegalStateException.class,
                () -> PresetLibrary.parse(List.of(indexWith("post"), body)),
                "Точка входа вне габаритов обязана отвергаться: через клетку за постройкой в неё не войти");
    }

    @Test
    void materials_BodyGainsOneBlock_CountsFollowBlocks() {
        String header = "name: Пол\ntype: infrastructure\nstage: 0\nsize: 3x1x1\nentrance: 2,0,0\n";
        Preset single = PresetLibrary.parse(List.of(indexWith("floor"), bodyContent(body(header, "C.."))))
                .byId("floor")
                .orElseThrow();
        Preset doubled = PresetLibrary.parse(List.of(indexWith("floor"), bodyContent(body(header, "CC."))))
                .byId("floor")
                .orElseThrow();

        assertEquals(
                1,
                single.materials().get("minecraft:cobblestone"),
                "Один блок в теле обязан дать один материал: число считается из блоков, а не пишется вручную");
        assertEquals(
                2,
                doubled.materials().get("minecraft:cobblestone"),
                "Два блока в теле обязаны дать два материала: ручное число, совпавшее случайно, развалилось бы "
                        + "на подлоге с лишним блоком");
    }

    @Test
    void materials_DoorTwoCells_CountsOneDoor() {
        String header = "name: Проём\ntype: infrastructure\nstage: 0\nsize: 1x2x1\nentrance: 0,0,0\n";
        Preset door = PresetLibrary.parse(List.of(indexWith("door"), bodyContent(body(header, "D", "D"))))
                .byId("door")
                .orElseThrow();

        assertEquals(
                Map.of("minecraft:oak_door", 1),
                door.materials(),
                "Дверь в двух клетках — один предмет: верхнюю половину ставит тот же предмет, иначе сундук демо "
                        + "и гейт доски требовали бы вдвое больше дверей");
    }

    @Test
    void byType_Defense_ReturnsPalisadeSection() {
        PresetLibrary library = loadReal();

        assertEquals(
                List.of("palisade_section"),
                library.byType(BuildingType.DEFENSE).stream().map(Preset::id).toList(),
                "Поиск по типу «оборона» обязан находить палисад-секцию: по нему стратег выбирает ограждение");
    }

    @Test
    void byStage_StageZero_ReturnsCampStructures() {
        PresetLibrary library = loadReal();

        assertEquals(
                List.of("palisade_section", "torch_post", "mine_entrance_v1"),
                library.byStage(0).stream().map(Preset::id).toList(),
                "Этап 0 «лагерь выживания» — палисад, факелы и вход в шахту: по этапу стратег решает, "
                        + "что строить сейчас");
    }

    @Test
    void byIdAndTypeAndStage_StorageHut_MatchesStorageAtStageOne() {
        PresetLibrary library = loadReal();

        assertEquals(
                List.of("storage_hut"),
                library.byTypeAndStage(BuildingType.STORAGE, 1).stream()
                        .map(Preset::id)
                        .toList(),
                "Склад-хижина обязана находиться одновременно по типу «склад» и этапу 1");
    }

    @Test
    void requires_StorageLevels_PointAtPreviousLevel() {
        PresetLibrary library = loadReal();

        for (int level = 1; level <= 4; level++) {
            Preset preset = library.byId("storage_v" + level).orElseThrow();
            assertEquals(
                    level,
                    preset.level(),
                    "Уровень склада " + level + " обязан нести свой номер: номер отличает ступень постройки");
            if (level == 1) {
                assertTrue(
                        preset.requires().isEmpty(),
                        "Первый уровень склада ничего не требует: предыдущего уровня у него нет");
            } else {
                assertEquals(
                        Optional.of("storage_v" + (level - 1)),
                        preset.requires(),
                        "Уровень " + level + " обязан требовать предыдущий: без цепочки upgrade перескочил бы ступень");
            }
        }
    }

    @Test
    void countMaterials_DuplicateBlockId_SumsCounts() {
        Schematic schematic =
                SchematicTextParser.parse(body("size: 3x1x1\n", "CC#"), "тест").schematic();
        Map<String, Integer> materials = PresetLibrary.countMaterials(schematic);

        assertEquals(
                2,
                materials.get("minecraft:cobblestone"),
                "Повтор одного блока обязан суммироваться: иначе нехватка материала на стройке");
        assertEquals(
                1, materials.get("minecraft:oak_planks"), "Второй вид блока обязан идти отдельной записью с числом 1");
    }

    @Test
    void bodyPaths_RealIndex_ReturnsOnePathPerPreset() {
        String indexJson = JsonResource.readClasspath(INDEX_PATH);

        List<String> paths = PresetLibrary.bodyPaths(indexJson, INDEX_PATH);

        assertEquals(
                16,
                paths.size(),
                "Индекс обязан назвать по телу на каждый из шестнадцати пресетов: пропуск тела оставил бы пресет без схемы");
        assertEquals(
                PresetLibrary.SCHEMATICS_ROOT + "storage_hut.txt",
                paths.get(2),
                "Порядок тел обязан совпадать с порядком пресетов в индексе: иначе метаданные склеятся с чужой схемой");
    }

    @Test
    void entry_WellWaterCenter_IsAboveWater() {
        Preset well = loadReal().byId("well").orElseThrow();

        assertEquals(
                new BlockCoord(1, 1, 1),
                well.entry(),
                "У колодца вход — клетка над водой: спуск к воде, а не в сплошной блок");
    }

    @Test
    void byType_Craft_ReturnsEnchantRoomLevels() {
        PresetLibrary library = loadReal();

        assertEquals(
                List.of("enchant_room_v1", "enchant_room_v2"),
                library.byType(BuildingType.CRAFT).stream().map(Preset::id).toList(),
                "Чаровальня обязана лежать в типе «ремесло»: по типу стратег находит постройку под стол чарования");
    }

    @Test
    void requires_EnchantRoomV2_PointsAtLevelOne() {
        PresetLibrary library = loadReal();

        Preset level1 = library.byId("enchant_room_v1").orElseThrow();
        Preset level2 = library.byId("enchant_room_v2").orElseThrow();

        assertEquals(
                1, level1.level(), "Первый уровень чаровальни несёт номер 1: номер называет ступень внутри постройки");
        assertTrue(level1.requires().isEmpty(), "Первый уровень ничего не требует: предыдущего уровня у него нет");
        assertEquals(
                2,
                level2.level(),
                "Второй уровень обязан нести номер 2: номер отличает ступень внутри одной постройки");
        assertEquals(
                Optional.of("enchant_room_v1"),
                level2.requires(),
                "Второй уровень обязан требовать первый: условие доступности «предыдущий уровень построен»");
    }

    @Test
    void byIdAndLevel_EnchantRoom_LevelsAreMonotone() {
        PresetLibrary library = loadReal();

        for (Preset preset : library.byType(BuildingType.CRAFT)) {
            if (preset.requires().isPresent()) {
                Preset previous = library.byId(preset.requires().get()).orElseThrow();
                assertTrue(
                        previous.level() > Preset.LEVEL_NONE && previous.level() < preset.level(),
                        "У пресета '" + preset.id() + "' предыдущий уровень '" + previous.id()
                                + "' обязан иметь меньший номер: иначе цепочка уровней не растёт");
            }
        }
    }

    @Test
    void parse_RequiresUnknownPreset_IsRejected() {
        LoadedContent body = bodyContent(body(
                "name: Зал\ntype: craft\nstage: 2\nlevel: 2\nrequires: ghost\n" + "size: 2x1x1\nentrance: 0,0,0\n",
                ".C"));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> PresetLibrary.parse(List.of(indexWith("room"), body)),
                "Требование неизвестного пресета обязано отвергаться: правило «предыдущий уровень построен» "
                        + "ссылается на постройку, которой нет");

        assertTrue(
                error.getMessage().contains("ghost"),
                "Ошибка обязана называть неизвестный id требования: без него непонятно, что чинить; фактически: "
                        + error.getMessage());
    }

    @Test
    void planned_RealIndex_DescribesFinalLevelWithoutScheme() {
        PresetLibrary library = loadReal();

        assertEquals(
                1,
                library.planned().size(),
                "Индекс обязан описать финальный уровень чаровальни метаданными, хотя схемы у него ещё нет");
        PlannedLevel finalLevel = library.planned().get(0);
        assertEquals(
                3,
                finalLevel.level(),
                "Финальный описанный уровень несёт номер 3 — следующий после второго построенного");
        assertEquals(
                Optional.of("enchant_room_v2"),
                finalLevel.requires(),
                "Финальный уровень требует второй: его ставят, только когда предыдущий построен");
        assertTrue(
                finalLevel.name().contains("40×30"),
                "Описание финального уровня обязано называть большое здание ~40×30: иначе потеряно, что там "
                        + "несколько чаровален");
    }

    @Test
    void parse_PlannedUnknownRequires_IsRejected() {
        LoadedContent index = new LoadedContent(
                "{\"version\":2,\"presets\":[\"hut\"],"
                        + "\"planned\":[{\"level\":3,\"name\":\"Финал\",\"requires\":\"ghost\"}]}",
                "тестовый индекс",
                TableStatus.Source.BUILTIN);
        LoadedContent body =
                bodyContent(body("name: Хижина\ntype: housing\nstage: 0\nsize: 2x1x1\n" + "entrance: 0,0,0\n", ".#"));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> PresetLibrary.parse(List.of(index, body)),
                "Запланированный уровень с неизвестным требованием обязан отвергаться: иначе финал числится "
                        + "доступным без предыдущей постройки");

        assertTrue(
                error.getMessage().contains("ghost"),
                "Ошибка обязана называть неизвестный id требования запланированного уровня; фактически: "
                        + error.getMessage());
    }
}
