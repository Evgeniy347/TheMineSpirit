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
    void parse_SignWithoutText_KeepsEmptyText() {
        String json = "{\"version\":1,\"size\":[1,1,1],\"blocks\":["
                + "{\"pos\":[0,0,0],\"block\":\"minecraft:oak_sign\"}]}";

        assertEquals("", SchematicParser.parse(json, ORIGIN).blocks().get(0).text(),
                "Блок без поля text несёт пустую надпись: старые схемы обязаны читаться без текста");
    }

    @Test
    void parse_SignWithText_KeepsCategory() {
        String json = "{\"version\":1,\"size\":[1,1,1],\"blocks\":["
                + "{\"pos\":[0,0,0],\"block\":\"minecraft:oak_sign\",\"text\":\"руда\"}]}";

        Schematic.Entry entry = SchematicParser.parse(json, ORIGIN).blocks().get(0);

        assertEquals("руда", entry.text(),
                "Надпись таблички обязана читаться из схемы: по ней раскладка выбирает сундук категории");
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
    void parse_ForeignVersion_IsRejected() {
        String json = "{\"version\":2,\"size\":[1,1,1],\"blocks\":[{\"pos\":[0,0,0],\"block\":\"stone\"}]}";

        assertThrows(IllegalStateException.class, () -> SchematicParser.parse(json, ORIGIN),
                "Чужая версия обязана отвергаться: схема из другого формата ставится не так");
    }
}
