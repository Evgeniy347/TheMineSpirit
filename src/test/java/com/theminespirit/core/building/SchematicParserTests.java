package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchematicParserTests {
    private static final String ORIGIN = "schematics/house.json";

    @Test
    void parse_ValidFile_ReadsBlocksBottomUp() {
        String json = "{\"version\":1,\"size\":[3,2,3],\"blocks\":["
                + "{\"pos\":[0,1,0],\"block\":\"oak_planks\"},"
                + "{\"pos\":[0,0,0],\"block\":\"cobblestone\"}]}";

        Schematic schematic = SchematicParser.parse(json, ORIGIN);
        List<Schematic.PlacedEntry> entries = schematic.entries(new BlockCoord(10, 64, 10));

        assertEquals(2, entries.size(),
                "Схема обязана прочитать оба блока: потеря блока даёт дырку в постройке");
        assertTrue(entries.get(0).pos().y() <= entries.get(1).pos().y(),
                "Блоки схемы обязаны идти снизу вверх: иначе верхний ставится без опоры");
        assertEquals("cobblestone", entries.get(0).block(),
                "Первым обязан идти нижний блок: иначе порядок слоёв перепутан");
    }

    @Test
    void parse_UnknownField_IsRejectedWithPath() {
        String json = "{\"version\":1,\"size\":[1,1,1],\"blocks\":[],\"extra\":1}";

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SchematicParser.parse(json, ORIGIN),
                "Неизвестное поле обязано отвергаться: опечатка в имени поля сделала бы блок мёртвым");

        assertTrue(error.getMessage().contains(ORIGIN),
                "Ошибка обязана называть путь файла: иначе непонятно, какую схему чинить");
    }

    @Test
    void parse_MissingBlock_IsRejected() {
        String json = "{\"version\":1,\"size\":[1,1,1],\"blocks\":[{\"pos\":[0,0,0]}]}";

        assertThrows(IllegalStateException.class, () -> SchematicParser.parse(json, ORIGIN),
                "Блок без block обязан отвергаться: без вида блока ставить нечего");
    }

    @Test
    void parse_PosOutsideSize_IsRejected() {
        String json = "{\"version\":1,\"size\":[1,1,1],\"blocks\":[{\"pos\":[0,1,0],\"block\":\"stone\"}]}";

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SchematicParser.parse(json, ORIGIN),
                "Позиция вне size обязана отвергаться: блок вне схемы ставить некуда");

        assertTrue(error.getMessage().contains(ORIGIN),
                "Ошибка обязана называть путь файла");
    }

    @Test
    void parse_EmptyBlocks_IsRejected() {
        String json = "{\"version\":1,\"size\":[1,1,1],\"blocks\":[]}";

        assertThrows(IllegalStateException.class, () -> SchematicParser.parse(json, ORIGIN),
                "Пустой список блоков обязан отвергаться: пустая схема ничего не строит");
    }

    @Test
    void parse_OversizeScheme_IsRejected() {
        String json = "{\"version\":1,\"size\":[33,1,1],\"blocks\":[{\"pos\":[0,0,0],\"block\":\"stone\"}]}";

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SchematicParser.parse(json, ORIGIN),
                "Схема сверх предела обязана отвергаться до старта: иначе стройка тянется часами");

        assertTrue(error.getMessage().contains("33"),
                "Ошибка обязана называть размер: иначе непонятно, что уменьшить");
    }

    @Test
    void parse_StateProperties_AreCarriedToPlacedEntries() {
        String json = "{\"version\":1,\"size\":[1,2,1],\"blocks\":["
                + "{\"pos\":[0,0,0],\"block\":\"oak_door\",\"state\":{\"facing\":\"north\",\"half\":\"lower\"}},"
                + "{\"pos\":[0,1,0],\"block\":\"oak_door\",\"state\":{\"facing\":\"north\",\"half\":\"upper\"}}]}";

        Schematic schematic = SchematicParser.parse(json, ORIGIN);
        List<Schematic.PlacedEntry> entries = schematic.entries(new BlockCoord(10, 64, 10));

        assertEquals("lower", entries.get(0).state().get("half"),
                "Схема обязана хранить состояние блока: без half дверь встанет одной половиной");
        assertEquals("upper", entries.get(1).state().get("half"),
                "Верхняя половина двери обязана нести своё состояние: иначе её не отличить от нижней");
        assertEquals("north", entries.get(1).state().get("facing"),
                "facing обязан читаться вместе с блоком: иначе дверь повернётся не туда");
    }

    @Test
    void parse_StateRotatedWithScheme_KeepsPropertiesAndTurnsPosition() {
        String json = "{\"version\":1,\"size\":[3,1,1],\"blocks\":["
                + "{\"pos\":[2,0,0],\"block\":\"oak_door\",\"state\":{\"facing\":\"north\",\"half\":\"lower\"}}]}";

        Schematic schematic = SchematicParser.parse(json, ORIGIN);
        Schematic.PlacedEntry turned = schematic.entries(new BlockCoord(10, 64, 10), BuildRotation.CLOCKWISE_90)
                .get(0);

        assertEquals(new BlockCoord(10, 64, 12), turned.pos(),
                "Позиция блока обязана повернуться вместе со схемой: иначе дверь останется на старом месте");
        assertEquals("north", turned.state().get("facing"),
                "Свойства блока обязаны дойти до постановки: поворот применяет движок блоков к тому же состоянию");
    }

    @Test
    void parse_StateNotObject_IsRejected() {
        String json = "{\"version\":1,\"size\":[1,1,1],\"blocks\":["
                + "{\"pos\":[0,0,0],\"block\":\"stone\",\"state\":\"facing=north\"}]}";

        assertThrows(IllegalStateException.class, () -> SchematicParser.parse(json, ORIGIN),
                "state строкой обязан отвергаться: свойства задаются объектом, иначе они не прочитаются");
    }

    @Test
    void parse_StateValueNotText_IsRejected() {
        String json = "{\"version\":1,\"size\":[1,1,1],\"blocks\":["
                + "{\"pos\":[0,0,0],\"block\":\"oak_door\",\"state\":{\"half\":2}}]}";

        assertThrows(IllegalStateException.class, () -> SchematicParser.parse(json, ORIGIN),
                "Числовое значение свойства обязано отвергаться: значения свойств блока задаются текстом");
    }

    @Test
    void parse_ForeignVersion_IsRejected() {
        String json = "{\"version\":2,\"size\":[1,1,1],\"blocks\":[{\"pos\":[0,0,0],\"block\":\"stone\"}]}";

        assertThrows(IllegalStateException.class, () -> SchematicParser.parse(json, ORIGIN),
                "Чужая версия обязана отвергаться: схема из другого формата ставится не так");
    }
}
