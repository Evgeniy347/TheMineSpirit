package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.LoadedContent;
import com.theminespirit.core.reload.TableStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DemoBuildingsTests {
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

    @Test
    void ids_Listed_ContainEveryChecklistBuilding() {
        assertEquals(
                List.of(
                        "family_house",
                        "player_house",
                        "lamp",
                        "stone_house",
                        "storage_hut",
                        "storage",
                        "well",
                        "plaza",
                        "palisade_section",
                        "pen",
                        "enchant_room",
                        "mine_entrance"),
                DemoBuildings.ids(),
                "Каталог демо-строек обязан содержать все постройки чеклиста: игрок наберёт /spirit demo <id> "
                        + "и не найдёт нужную");
    }

    @Test
    void level_EachScheme_ResolvesToBodyOrPreset() {
        for (DemoBuildings.Building building : DemoBuildings.all()) {
            for (DemoBuildings.Level level : building.levels()) {
                String path = PresetLibrary.SCHEMATICS_ROOT + level.scheme() + SchematicTextParser.EXTENSION;
                String body = JsonResource.readClasspath(path);
                assertTrue(
                        body.contains("layers:"),
                        "Схема уровня '" + level.scheme() + "' постройки '" + building.id()
                                + "' обязана читаться из ресурсов мода: иначе демо-стройка не найдёт тело");
            }
        }
    }

    @Test
    void pen_EachLevel_UsesItsOwnScheme() {
        DemoBuildings.Building pen = DemoBuildings.require("pen");

        assertEquals(
                "pen_4x5",
                pen.level(1).scheme(),
                "Первый уровень загона — 4×5: иначе upgrade построит не тот младший уровень");
        assertEquals("pen_7x7", pen.level(2).scheme(), "Второй уровень загона — 7×7");
        assertEquals("pen_9x9", pen.level(3).scheme(), "Третий уровень загона — 9×9");
        assertNotEquals(
                pen.level(1).scheme(),
                pen.level(2).scheme(),
                "Уровни загона обязаны различаться схемами: одинаковые схемы сделали бы upgrade бессмысленным");
    }

    @Test
    void materials_PenLevel2_MatchPen7x7Preset() {
        PresetLibrary library = loadReal();
        String scheme = DemoBuildings.require("pen").level(2).scheme();
        Preset preset = library.byId(scheme).orElseThrow();

        assertEquals(
                PresetLibrary.countMaterials(preset.schematic()),
                preset.materials(),
                "Материалы уровня берутся из тела схемы: сундук обязан вмещать ровно то, из чего строится");
        assertTrue(
                preset.materials().values().stream().anyMatch(count -> count > 0),
                "У схемы обязаны быть материалы: пустой сундук нечего строить");
    }

    @Test
    void enchantRoom_LevelTwo_RequiresLevelOne() {
        PresetLibrary library = loadReal();
        Preset levelTwo = library.byId(
                        DemoBuildings.require("enchant_room").level(2).scheme())
                .orElseThrow();

        assertEquals(
                "enchant_room_v1",
                levelTwo.requires().orElseThrow(),
                "Второй уровень чаровальни строится поверх первого: без требования upgrade заказал бы "
                        + "второй уровень на пустом месте");
    }

    @Test
    void type_PresetSchemes_MatchPresetType() {
        PresetLibrary library = loadReal();
        for (DemoBuildings.Building building : DemoBuildings.all()) {
            for (DemoBuildings.Level level : building.levels()) {
                Preset preset = library.byId(level.scheme()).orElse(null);
                if (preset != null) {
                    assertEquals(
                            preset.type(),
                            building.type(),
                            "Тип демо-постройки '" + building.id() + "' обязан совпадать с типом пресета '"
                                    + preset.id() + "': иначе сторож схем применит к жилью правила склада");
                }
            }
        }
        assertEquals(
                BuildingType.HOUSING,
                DemoBuildings.require("family_house").type(),
                "Дом семьи — жильё: без типа жилья сторож не проверит у него окна, дверь и свет");
    }

    @Test
    void level_UnknownRequested_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DemoBuildings.require("pen").level(4),
                "Четвёртого уровня загона нет: заказ несуществующего уровня обязан отвергаться с подсказкой");
    }

    @Test
    void parse_DuplicateId_IsRejected() {
        String json = "{\"buildings\":[{\"id\":\"a\",\"name\":\"А\",\"type\":\"housing\",\"levels\":[{\"level\":1,"
                + "\"scheme\":\"x\"}]},{\"id\":\"a\",\"name\":\"Б\",\"type\":\"housing\",\"levels\":[{\"level\":1,"
                + "\"scheme\":\"y\"}]}]}";

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DemoBuildings.parse(json, "тест"),
                "Каталог с ошибкой обязан отвергаться при разборе, а не молча");
        assertTrue(
                error.getMessage().contains("дважды"),
                "Каталог с двумя постройками одного id обязан отвергаться с причиной, получено: " + error.getMessage());
    }

    @Test
    void parse_IdOfDemoSubcommand_IsRejected() {
        String json = "{\"buildings\":[{\"id\":\"city\",\"name\":\"Город\",\"type\":\"housing\",\"levels\":["
                + "{\"level\":1,\"scheme\":\"x\"}]}]}";

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DemoBuildings.parse(json, "тест"),
                "Постройка с именем подкоманды обязана отвергаться: /spirit demo city затенила бы её");
        assertTrue(
                error.getMessage().contains("подкоманды"),
                "Отказ обязан назвать конфликт с подкомандой, получено: " + error.getMessage());
    }

    @Test
    void parse_UnknownType_IsRejected() {
        String json = "{\"buildings\":[{\"id\":\"a\",\"name\":\"А\",\"type\":\"castle\",\"levels\":[{\"level\":1,"
                + "\"scheme\":\"x\"}]}]}";

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> DemoBuildings.parse(json, "тест"),
                "Каталог с ошибкой обязан отвергаться при разборе, а не молча");
        assertTrue(
                error.getMessage().contains("castle"),
                "Неизвестный тип постройки обязан называться в отказе, получено: " + error.getMessage());
    }
}
