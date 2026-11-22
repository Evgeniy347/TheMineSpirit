package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.inventory.api.JsonResource;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.api.LoadedContent;
import com.theminespirit.core.reload.api.TableStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PresetLibraryTestsPart2 {
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
    void byId_Pen4x5_FourByFiveWithGate() {
        Preset pen = loadReal().byId("pen_4x5").orElseThrow();

        int minX = pen.schematic().blocks().stream()
                .mapToInt(block -> block.dx())
                .min()
                .orElseThrow();
        int maxX = pen.schematic().blocks().stream()
                .mapToInt(block -> block.dx())
                .max()
                .orElseThrow();
        int minZ = pen.schematic().blocks().stream()
                .mapToInt(block -> block.dz())
                .min()
                .orElseThrow();
        int maxZ = pen.schematic().blocks().stream()
                .mapToInt(block -> block.dz())
                .max()
                .orElseThrow();
        assertEquals(
                List.of(4, 5),
                List.of(maxX - minX + 1, maxZ - minZ + 1),
                "Загон уровня 1 — минимум 4×5: меньший загон не вместит пару животных для разведения"
                        + " Иначе тест byId_Pen4x5_FourByFiveWithGate перестанет ловить эту поломку. При смене byId_Pen4x5_FourByFiveWithGate проверь это утверждение.");
        assertTrue(
                pen.schematic().blocks().stream()
                        .anyMatch(block -> block.block().contains("fence_gate")),
                "Загон уровня 1 обязан иметь калитку: без неё животное не загнать"
                        + " Иначе тест byId_Pen4x5_FourByFiveWithGate перестанет ловить эту поломку. При смене byId_Pen4x5_FourByFiveWithGate проверь это утверждение.");
    }

    @Test
    void entry_WellWaterCenter_IsAboveWater() {
        Preset well = loadReal().byId("well").orElseThrow();

        assertEquals(
                new BlockCoord(7, 1, 7),
                well.entry(),
                "У колодца вход сдвинут в центр участка 16×16 и стоит над водой: спуск к воде, а не в сплошной блок"
                        + " Иначе тест entry_WellWaterCenter_IsAboveWater перестанет ловить эту поломку. При смене entry_WellWaterCenter_IsAboveWater проверь это утверждение.");
    }

    @Test
    void byType_Craft_ReturnsEnchantRoomLevels() {
        PresetLibrary library = loadReal();

        assertEquals(
                List.of(
                        "enchant_room_v1",
                        "enchant_room_v2",
                        "enchant_room_v3",
                        "lumber_yard_1",
                        "lumber_yard_2",
                        "workshop_1",
                        "workshop_2",
                        "workshop_3",
                        "forge_1",
                        "weaver_1",
                        "forge_2",
                        "forge_3",
                        "weaver_2",
                        "alchemy_1",
                        "alchemy_2",
                        "alchemy_3",
                        "alchemy_4",
                        "enchant_room_v4"),
                library.byType(BuildingType.CRAFT).stream().map(Preset::id).toList(),
                "Чаровальня обязана лежать в типе «ремесло»: по типу стратег находит постройку под стол чарования"
                        + " Иначе тест byType_Craft_ReturnsEnchantRoomLevels перестанет ловить эту поломку. При смене byType_Craft_ReturnsEnchantRoomLevels проверь это утверждение.");
    }

    @Test
    void requires_EnchantRoomV2_PointsAtLevelOne() {
        PresetLibrary library = loadReal();

        Preset level1 = library.byId("enchant_room_v1").orElseThrow();
        Preset level2 = library.byId("enchant_room_v2").orElseThrow();

        assertEquals(
                1,
                level1.level(),
                "Первый уровень чаровальни несёт номер 1: номер называет ступень внутри постройки"
                        + " Иначе тест requires_EnchantRoomV2_PointsAtLevelOne перестанет ловить эту поломку. При смене requires_EnchantRoomV2_PointsAtLevelOne проверь это утверждение.");
        assertTrue(
                level1.requires().isEmpty(),
                "Первый уровень ничего не требует: предыдущего уровня у него нет"
                        + " Иначе тест requires_EnchantRoomV2_PointsAtLevelOne перестанет ловить эту поломку. При смене requires_EnchantRoomV2_PointsAtLevelOne проверь это утверждение.");
        assertEquals(
                2,
                level2.level(),
                "Второй уровень обязан нести номер 2: номер отличает ступень внутри одной постройки"
                        + " Иначе тест requires_EnchantRoomV2_PointsAtLevelOne перестанет ловить эту поломку. При смене requires_EnchantRoomV2_PointsAtLevelOne проверь это утверждение.");
        assertEquals(
                Optional.of("enchant_room_v1"),
                level2.requires(),
                "Второй уровень обязан требовать первый: условие доступности «предыдущий уровень построен»"
                        + " Иначе тест requires_EnchantRoomV2_PointsAtLevelOne перестанет ловить эту поломку. При смене requires_EnchantRoomV2_PointsAtLevelOne проверь это утверждение.");
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
                                + "' обязан иметь меньший номер: иначе цепочка уровней не растёт"
                                + " При смене byIdAndLevel_EnchantRoom_LevelsAreMonotone проверь это утверждение.");
            }
        }
    }

    @Test
    void parse_RequiresUnknownPreset_IsRejected() {
        LoadedContent body = bodyContent(body("""
                name: Зал
                type: craft
                stage: 2
                level: 2
                requires: ghost
                size: 2x1x1
                entrance: 0,0,0
                """, ".C"));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> PresetLibrary.parse(List.of(indexWith("room"), body)),
                "Требование неизвестного пресета обязано отвергаться: правило «предыдущий уровень построен» "
                        + "ссылается на постройку, которой нет"
                        + " Иначе тест parse_RequiresUnknownPreset_IsRejected перестанет ловить эту поломку. При смене parse_RequiresUnknownPreset_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("ghost"),
                "Ошибка обязана называть неизвестный id требования: без него непонятно, что чинить; фактически: "
                        + error.getMessage()
                        + " Иначе тест parse_RequiresUnknownPreset_IsRejected перестанет ловить эту поломку. При смене parse_RequiresUnknownPreset_IsRejected проверь это утверждение.");
    }

    @Test
    void realIndex_EnchantRoomFinalLevel_HasScheme() {
        PresetLibrary library = loadReal();

        assertEquals(
                List.of(),
                library.planned(),
                "Все уровни чаровальни описаны схемами: запланированный уровень без схемы остаётся запасным "
                        + "механизмом, а не способом держать финал на бумаге"
                        + " Иначе тест realIndex_EnchantRoomFinalLevel_HasScheme перестанет ловить эту поломку. При смене realIndex_EnchantRoomFinalLevel_HasScheme проверь это утверждение.");
        Preset finalLevel = library.byId("enchant_room_v3").orElseThrow();
        assertEquals(
                3,
                finalLevel.level(),
                "Финальный уровень чаровальни несёт номер 3"
                        + " Иначе тест realIndex_EnchantRoomFinalLevel_HasScheme перестанет ловить эту поломку. При смене realIndex_EnchantRoomFinalLevel_HasScheme проверь это утверждение.");
        assertEquals(
                Optional.of("enchant_room_v2"),
                finalLevel.requires(),
                "Финальный уровень требует второй: его ставят, только когда предыдущий построен"
                        + " Иначе тест realIndex_EnchantRoomFinalLevel_HasScheme перестанет ловить эту поломку. При смене realIndex_EnchantRoomFinalLevel_HasScheme проверь это утверждение.");
        assertEquals(
                BuildingType.CRAFT,
                finalLevel.type(),
                "Чаровальня остаётся постройкой ремесла: от типа зависят правила схем"
                        + " Иначе тест realIndex_EnchantRoomFinalLevel_HasScheme перестанет ловить эту поломку. При смене realIndex_EnchantRoomFinalLevel_HasScheme проверь это утверждение.");
    }

    @Test
    void parse_PlannedUnknownRequires_IsRejected() {
        LoadedContent index = new LoadedContent(
                "{\"version\":2,\"presets\":[\"hut\"],"
                        + "\"planned\":[{\"level\":3,\"name\":\"Финал\",\"requires\":\"ghost\"}]}",
                "тестовый индекс",
                TableStatus.Source.BUILTIN);
        LoadedContent body = bodyContent(body("""
                                 name: Хижина
                                 type: housing
                                 stage: 0
                                 size: 2x1x1
                                 entrance: 0,0,0
                                 """, ".#"));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> PresetLibrary.parse(List.of(index, body)),
                "Запланированный уровень с неизвестным требованием обязан отвергаться: иначе финал числится "
                        + "доступным без предыдущей постройки"
                        + " При смене parse_PlannedUnknownRequires_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("ghost"),
                "Ошибка обязана называть неизвестный id требования запланированного уровня; фактически: "
                        + error.getMessage()
                        + " Иначе тест parse_PlannedUnknownRequires_IsRejected перестанет ловить эту поломку. При смене parse_PlannedUnknownRequires_IsRejected проверь это утверждение.");
    }
}
