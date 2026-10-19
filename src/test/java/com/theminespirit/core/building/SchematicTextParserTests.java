package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SchematicTextParserTests {
    private static final String HUT = """
            # хижина для теста
            name: "Хижина: тест"
            type: housing
            stage: 1
            level: 2
            requires: hut_v1
            size: 3x3x2   # Ш×В×Д
            entrance: front
            legend:
              '#': oak_planks   # стены
              'D': oak_door[facing=north,hinge=left]
              'S': oak_sign "склад"
            layers:
              - y0 пол: |
                  #D#
                  #.S
              - y1: |
                  #D#
                  # #
              - y2: |
                  ###
                  ###
            """;

    private static SchematicDocument parse(String text) {
        return SchematicTextParser.parse(text, "тест.txt");
    }

    private static IllegalStateException rejected(String text, String why) {
        return assertThrows(IllegalStateException.class, () -> parse(text), why);
    }

    @Test
    void parse_TrailingCommaInBlockState_IsRejected() {
        rejected(
                box("  'B': white_bed[facing=north,]\n", "B./B."),
                "Свойство после хвостовой запятой пустое: состояние «facing=north,» — опечатка, молча принятая "
                        + "она прячет потерянное свойство блока");
    }

    private static String box(String legend, String... layers) {
        StringBuilder text = new StringBuilder("size: 2x")
                .append(layers.length)
                .append("x2\nlegend:\n")
                .append(legend)
                .append("layers:\n");
        for (int y = 0; y < layers.length; y++) {
            text.append("  - y").append(y).append(": |\n");
            for (String row : layers[y].split("/", -1)) {
                text.append("      ").append(row).append('\n');
            }
        }
        return text.toString();
    }

    @Test
    void parse_Header_ReadsAllKeys() {
        SchematicDocument.Header header = parse(HUT).header();

        assertEquals(
                Optional.of("Хижина: тест"),
                header.name(),
                "Название в кавычках читается целиком, двоеточие внутри не обрезает его");
        assertEquals(Optional.of(BuildingType.HOUSING), header.type(), "type задаёт правила жилья");
        assertEquals(Optional.of(1), header.stage(), "stage — этап развития поселения");
        assertEquals(2, header.level(), "level — ступень постройки");
        assertEquals(Optional.of("hut_v1"), header.requires(), "requires — предыдущий уровень");
        assertEquals(
                Optional.of(new BlockCoord(1, 0, 0)),
                header.entrance(),
                "entrance: front — нижняя половина двери в первой строке слоёв: по ней ставится вход");
    }

    @Test
    void parse_Layers_FirstRowIsFrontAndXGoesRight() {
        Schematic schematic = parse(HUT).schematic();

        assertTrue(
                schematic.blocks().contains(new Schematic.Entry(2, 0, 1, "minecraft:oak_sign", Map.of(), "склад")),
                "Третий символ второй строки слоя y0 — клетка x=2, z=1: первая строка — фронт z=0, символы "
                        + "слева направо — +X; табличка несёт свой текст");
        assertTrue(
                schematic.air().contains(new Schematic.Cell(1, 0, 1)),
                "Точка — клетка, обязанная быть воздухом: стройка её очищает");
        assertTrue(
                schematic.blocks().stream().noneMatch(e -> e.dx() == 1 && e.dy() == 1 && e.dz() == 1),
                "Пробел — клетку не трогать: ни блока, ни воздуха");
        assertTrue(
                schematic.air().stream().noneMatch(cell -> cell.equals(new Schematic.Cell(1, 1, 1))),
                "Пробел не превращается в обязательный воздух");
    }

    @Test
    void parse_DoorInTwoLayers_BecomesLowerAndUpperHalf() {
        Schematic schematic = parse(HUT).schematic();

        assertTrue(
                schematic
                        .blocks()
                        .contains(new Schematic.Entry(
                                1,
                                0,
                                0,
                                "minecraft:oak_door",
                                Map.of("facing", "north", "hinge", "left", "half", "lower"),
                                "")),
                "Нижняя клетка двери получает half=lower сама: в файле половина не пишется");
        assertTrue(
                schematic
                        .blocks()
                        .contains(new Schematic.Entry(
                                1,
                                1,
                                0,
                                "minecraft:oak_door",
                                Map.of("facing", "north", "hinge", "left", "half", "upper"),
                                "")),
                "Верхняя клетка двери получает half=upper: дверь целиком, а не половина");
        assertEquals(
                1,
                PresetLibrary.countMaterials(schematic).get("minecraft:oak_door"),
                "Дверь в двух клетках — один предмет");
    }

    @Test
    void parse_BedFacingNorth_HeadIsTowardsFront() {
        Schematic schematic =
                parse(box("  'B': white_bed[facing=north]\n", "B./B.")).schematic();

        assertTrue(
                schematic
                        .blocks()
                        .contains(new Schematic.Entry(
                                0, 0, 0, "minecraft:white_bed", Map.of("facing", "north", "part", "head"), "")),
                "Изголовье кровати — клетка по направлению facing: north — к фронту (z=0)");
        assertTrue(
                schematic
                        .blocks()
                        .contains(new Schematic.Entry(
                                0, 0, 1, "minecraft:white_bed", Map.of("facing", "north", "part", "foot"), "")),
                "Ножная часть — позади изголовья: иначе ставится половина кровати");
    }

    @Test
    void parse_BedWithoutPair_NamesCellAndFacing() {
        IllegalStateException error = rejected(
                box("  'B': white_bed[facing=east]\n", "B./.."),
                "Кровать в одну клетку обязана отвергаться: в мире встанет только ножная половина");

        assertTrue(
                error.getMessage().contains("[0,0,0]") && error.getMessage().contains("facing=east"),
                "Ошибка обязана называть клетку и facing: без них непонятно, куда дописать изголовье; "
                        + error.getMessage());
    }

    @Test
    void parse_BedWithoutFacing_IsRejected() {
        IllegalStateException error = rejected(
                box("  'B': white_bed\n", "BB/.."),
                "Кровать без facing обязана отвергаться: по нему находится изголовье");

        assertTrue(error.getMessage().contains("facing"), "Ошибка называет facing: " + error.getMessage());
    }

    @Test
    void parse_DoorWithoutUpperHalf_NamesLayer() {
        IllegalStateException error = rejected(
                box("  'D': oak_door\n", "D./..", "../.."),
                "Дверь в одной клетке обязана отвергаться: в мире встанет полдвери");

        assertTrue(
                error.getMessage().contains("y1"),
                "Ошибка обязана называть слой, куда дописать верхнюю половину; " + error.getMessage());
    }

    @Test
    void parse_UnknownSymbol_NamesLineAndColumn() {
        IllegalStateException error = rejected(
                box("  '#': oak_planks\n", "#./.X"),
                "Символ без легенды обязан отвергаться: иначе клетка молча пропадёт");

        assertTrue(
                error.getMessage().contains("'X'")
                        && error.getMessage().contains("строка 7")
                        && error.getMessage().contains("x=1"),
                "Ошибка обязана называть символ, строку файла и позицию в строке; " + error.getMessage());
    }

    @Test
    void parse_RowLongerThanWidth_IsRejected() {
        IllegalStateException error = rejected(
                box("  '#': oak_planks\n", "###/.."),
                "Строка шире size обязана отвергаться: лишний блок вышел бы за габарит");

        assertTrue(error.getMessage().contains("ширина 2"), "Ошибка называет ширину: " + error.getMessage());
    }

    @Test
    void parse_ShortRow_PadsWithUntouchedCells() {
        Schematic schematic = parse(box("  '#': oak_planks\n", "#/..")).schematic();

        assertEquals(
                List.of(new Schematic.Entry(0, 0, 0, "minecraft:oak_planks")),
                schematic.blocks(),
                "Короткая строка дополняется пробелами («не трогать»): редактор, срезавший пробелы в конце, "
                        + "не ломает файл");
        assertEquals(
                List.of(new Schematic.Cell(0, 0, 1), new Schematic.Cell(1, 0, 1)),
                schematic.air(),
                "Точки второй строки остаются обязательным воздухом");
    }

    @Test
    void parse_WrongRowCount_NamesLayerAndDepth() {
        IllegalStateException error = rejected(
                box("  '#': oak_planks\n", "##"),
                "Слой с числом строк меньше глубины обязан отвергаться: тыл постройки пропал бы");

        assertTrue(
                error.getMessage().contains("y0") && error.getMessage().contains("глубина 2"),
                "Ошибка называет слой и глубину: " + error.getMessage());
    }

    @Test
    void parse_MissingLayer_IsRejected() {
        String text = "size: 1x2x1\nlegend:\n  '#': oak_planks\nlayers:\n  - y0: |\n      #\n";

        IllegalStateException error = rejected(text, "Пропущенный слой обязан отвергаться: этаж не достроится");

        assertTrue(error.getMessage().contains("y1"), "Ошибка называет пропущенный слой: " + error.getMessage());
    }

    @Test
    void parse_ReservedSymbolInLegend_IsRejected() {
        rejected(box("  '.': stone\n", "../.."), "Точку нельзя переопределить: она значит «обязан быть воздух»");
    }

    @Test
    void parse_DuplicateKey_IsRejected() {
        IllegalStateException error = rejected(
                "size: 1x1x1\nsize: 2x1x1\n",
                "Повтор ключа обязан отвергаться: второе значение молча перекрыло бы первое");

        assertTrue(error.getMessage().contains("строка 2"), "Ошибка называет строку повтора: " + error.getMessage());
    }

    @Test
    void parse_UnknownKey_ListsAllowedKeys() {
        IllegalStateException error = rejected("sise: 1x1x1\n", "Опечатка в ключе обязана отвергаться");

        assertTrue(
                error.getMessage().contains("sise") && error.getMessage().contains("size"),
                "Ошибка называет опечатку и допустимые ключи: " + error.getMessage());
    }

    @Test
    void parse_EntranceFrontWithoutDoor_IsRejected() {
        rejected(
                "size: 1x1x1\nentrance: front\nlegend:\n  '#': oak_planks\nlayers:\n  - y0: |\n      #\n",
                "entrance: front без двери во фронте обязан отвергаться: вход неизвестен");
    }

    @Test
    void parse_AirInLegend_IsRejected() {
        rejected(box("  'a': air\n", "a./.."), "Воздух в легенде не задаётся: для него есть точка");
    }
}
