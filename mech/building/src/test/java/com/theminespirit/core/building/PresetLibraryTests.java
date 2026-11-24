package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.inventory.api.JsonResource;
import com.theminespirit.core.reload.api.LoadedContent;
import com.theminespirit.core.reload.api.TableStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PresetLibraryTests {
    private static final String INDEX_PATH = PresetLibrary.SCHEMATICS_ROOT + PresetLibrary.INDEX_ID + ".json";

    private static final List<String> PRESET_IDS = PresetIds.ALL;

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
            for (String row : rows[y].split("/", -1)) {
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
    void parse_RealIndex_LoadsAllPresets() {
        PresetLibrary library = loadReal();

        assertEquals(
                125,
                library.size(),
                "Библиотека обязана прочитать все пресеты, включая фрагменты каменной стены (прямые, столб, углы 90° и 45°, ворота), четыре уровня дома семьи и дома игрока, площадь 1–3, колодец 2, светильники 2–3, три уровня чаровальни, "
                        + "четыре уровня склада, загоны трёх уровней и сад: без них стратегу не из чего "
                        + "составлять планировку поселения"
                        + " Иначе тест parse_RealIndex_LoadsAllPresets перестанет ловить эту поломку. При смене parse_RealIndex_LoadsAllPresets проверь это утверждение.");
        assertEquals(
                PRESET_IDS,
                library.ids(),
                "Состав пресетов обязан совпадать с индексом: пропажа пресета ломает застройку этапа"
                        + " При смене parse_RealIndex_LoadsAllPresets проверь это утверждение.");
    }

    @Test
    void parse_EachRealPreset_PassesLibraryChecks() {
        PresetLibrary library = loadReal();

        for (Preset preset : library.all()) {
            assertEquals(
                    preset.materials(),
                    PresetLibrary.countMaterials(preset.schematic()),
                    "У пресета '" + preset.id() + "' материалы обязаны считаться из блоков, а не задаваться вручную"
                            + " Иначе тест parse_EachRealPreset_PassesLibraryChecks перестанет ловить эту поломку. При смене parse_EachRealPreset_PassesLibraryChecks проверь это утверждение.");
            assertTrue(
                    preset.entry().x() >= 0
                            && preset.entry().x() < preset.sizeX()
                            && preset.entry().y() >= 0
                            && preset.entry().y() < preset.sizeY()
                            && preset.entry().z() >= 0
                            && preset.entry().z() < preset.sizeZ(),
                    "У пресета '" + preset.id() + "' точка входа обязана лежать внутри габаритов, иначе в постройку "
                            + "не войти: " + preset.entry()
                            + " При смене parse_EachRealPreset_PassesLibraryChecks проверь это утверждение.");
            assertTrue(
                    preset.stage() >= Preset.STAGE_MIN && preset.stage() <= Preset.STAGE_MAX,
                    "У пресета '" + preset.id() + "' этап развития обязан быть 0..4, фактически " + preset.stage()
                            + " Иначе тест parse_EachRealPreset_PassesLibraryChecks перестанет ловить эту поломку. При смене parse_EachRealPreset_PassesLibraryChecks проверь это утверждение.");
        }
    }

    @Test
    void parse_PresetWithoutName_IsRejected() {
        LoadedContent body = bodyContent(body("type: housing\nstage: 0\nsize: 2x1x1\nentrance: 0,0,0\n", ".C"));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> PresetLibrary.parse(List.of(indexWith("hut"), body)),
                "Пресет без названия обязан отвергаться: /spirit presets и стратег показывают постройку по имени"
                        + " Иначе тест parse_PresetWithoutName_IsRejected перестанет ловить эту поломку. При смене parse_PresetWithoutName_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("name"),
                "Ошибка обязана называть недостающий ключ name; фактически: " + error.getMessage()
                        + " Иначе тест parse_PresetWithoutName_IsRejected перестанет ловить эту поломку. При смене parse_PresetWithoutName_IsRejected проверь это утверждение.");
    }

    @Test
    void parse_EntryInsideSolidBlock_IsRejected() {
        LoadedContent body = bodyContent(body("""
                     name: Столб
                     type: infrastructure
                     stage: 0
                     size: 1x1x1
                     entrance: 0,0,0
                     """, "#"));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> PresetLibrary.parse(List.of(indexWith("post"), body)),
                "Точка входа, занятая сплошным блоком, обязана отвергаться: сквозь стену не войти"
                        + " Иначе тест parse_EntryInsideSolidBlock_IsRejected перестанет ловить эту поломку. При смене parse_EntryInsideSolidBlock_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("вход"),
                "Ошибка обязана называть точку входа: без неё непонятно, где проём; фактически: " + error.getMessage()
                        + " Иначе тест parse_EntryInsideSolidBlock_IsRejected перестанет ловить эту поломку. При смене parse_EntryInsideSolidBlock_IsRejected проверь это утверждение.");
    }

    @Test
    void parse_EntryOutsideSize_IsRejected() {
        LoadedContent body = bodyContent(body("""
                     name: Столб
                     type: infrastructure
                     stage: 0
                     size: 1x1x1
                     entrance: 2,0,0
                     """, "#"));

        assertThrows(
                IllegalStateException.class,
                () -> PresetLibrary.parse(List.of(indexWith("post"), body)),
                "Точка входа вне габаритов обязана отвергаться: через клетку за постройкой в неё не войти"
                        + " Иначе тест parse_EntryOutsideSize_IsRejected перестанет ловить эту поломку. При смене parse_EntryOutsideSize_IsRejected проверь это утверждение.");
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
                "Один блок в теле обязан дать один материал: число считается из блоков, а не пишется вручную"
                        + " Иначе тест materials_BodyGainsOneBlock_CountsFollowBlocks перестанет ловить эту поломку. При смене materials_BodyGainsOneBlock_CountsFollowBlocks проверь это утверждение.");
        assertEquals(
                2,
                doubled.materials().get("minecraft:cobblestone"),
                "Два блока в теле обязаны дать два материала: ручное число, совпавшее случайно, развалилось бы "
                        + "на подлоге с лишним блоком"
                        + " Иначе тест materials_BodyGainsOneBlock_CountsFollowBlocks перестанет ловить эту поломку. При смене materials_BodyGainsOneBlock_CountsFollowBlocks проверь это утверждение.");
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
                        + "и гейт доски требовали бы вдвое больше дверей"
                        + " При смене materials_DoorTwoCells_CountsOneDoor проверь это утверждение.");
    }

    @Test
    void byStage_StageZero_ReturnsCampStructures() {
        PresetLibrary library = loadReal();

        assertEquals(
                List.of(
                        "palisade_section",
                        "torch_post",
                        "mine_entrance_v1",
                        "mine_descent_v1",
                        "mine_tunnel_v1",
                        "player_house_1",
                        "plaza_1"),
                library.byStage(0).stream().map(Preset::id).toList(),
                "Этап 0 «лагерь выживания» — палисад, факелы, вход в шахту, спуск и ход, первый каменный дом и площадь: по этапу стратег решает, "
                        + "что строить сейчас"
                        + " Иначе тест byStage_StageZero_ReturnsCampStructures перестанет ловить эту поломку. При смене byStage_StageZero_ReturnsCampStructures проверь это утверждение.");
    }

    @Test
    void byIdAndTypeAndStage_StorageHut_MatchesStorageAtStageOne() {
        PresetLibrary library = loadReal();

        assertEquals(
                List.of("storage_hut"),
                library.byTypeAndStage(BuildingType.STORAGE, 1).stream()
                        .map(Preset::id)
                        .toList(),
                "Склад-хижина обязана находиться одновременно по типу «склад» и этапу 1"
                        + " Иначе тест byIdAndTypeAndStage_StorageHut_MatchesStorageAtStageOne перестанет ловить эту поломку. При смене byIdAndTypeAndStage_StorageHut_MatchesStorageAtStageOne проверь это утверждение.");
    }

    @Test
    void requires_StorageLevels_PointAtPreviousLevel() {
        PresetLibrary library = loadReal();

        for (int level = 1; level <= 4; level++) {
            Preset preset = library.byId("storage_v" + level).orElseThrow();
            assertEquals(
                    level,
                    preset.level(),
                    "Уровень склада " + level + " обязан нести свой номер: номер отличает ступень постройки"
                            + " Иначе тест requires_StorageLevels_PointAtPreviousLevel перестанет ловить эту поломку. При смене requires_StorageLevels_PointAtPreviousLevel проверь это утверждение.");
            if (level == 1) {
                assertTrue(
                        preset.requires().isEmpty(),
                        "Первый уровень склада ничего не требует: предыдущего уровня у него нет"
                                + " Иначе тест requires_StorageLevels_PointAtPreviousLevel перестанет ловить эту поломку. При смене requires_StorageLevels_PointAtPreviousLevel проверь это утверждение.");
            } else {
                assertEquals(
                        Optional.of("storage_v" + (level - 1)),
                        preset.requires(),
                        "Уровень " + level + " обязан требовать предыдущий: без цепочки upgrade перескочил бы ступень"
                                + " Иначе тест requires_StorageLevels_PointAtPreviousLevel перестанет ловить эту поломку. При смене requires_StorageLevels_PointAtPreviousLevel проверь это утверждение.");
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
                "Повтор одного блока обязан суммироваться: иначе нехватка материала на стройке"
                        + " При смене countMaterials_DuplicateBlockId_SumsCounts проверь это утверждение.");
        assertEquals(
                1,
                materials.get("minecraft:oak_planks"),
                "Второй вид блока обязан идти отдельной записью с числом 1"
                        + " Иначе тест countMaterials_DuplicateBlockId_SumsCounts перестанет ловить эту поломку. При смене countMaterials_DuplicateBlockId_SumsCounts проверь это утверждение.");
    }

    @Test
    void bodyPaths_RealIndex_ReturnsOnePathPerPreset() {
        String indexJson = JsonResource.readClasspath(INDEX_PATH);

        List<String> paths = PresetLibrary.bodyPaths(indexJson, INDEX_PATH);

        assertEquals(
                125,
                paths.size(),
                "Индекс обязан назвать по телу на каждый пресет: пропуск тела оставил бы пресет без схемы"
                        + " Иначе тест bodyPaths_RealIndex_ReturnsOnePathPerPreset перестанет ловить эту поломку. При смене bodyPaths_RealIndex_ReturnsOnePathPerPreset проверь это утверждение.");
        assertEquals(
                PresetLibrary.SCHEMATICS_ROOT + "storage_hut.txt",
                paths.get(2),
                "Порядок тел обязан совпадать с порядком пресетов в индексе: иначе метаданные склеятся с чужой схемой"
                        + " При смене bodyPaths_RealIndex_ReturnsOnePathPerPreset проверь это утверждение.");
    }
}
