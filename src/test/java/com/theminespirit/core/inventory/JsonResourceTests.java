package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonResourceTests {
    @Test
    void readClasspath_NullPath_FailsFastOnContract() {
        NullPointerException error = assertThrows(NullPointerException.class,
                () -> JsonResource.readClasspath(null),
                "Путь null обязан падать на контракте, а не молча искать файл без имени: "
                        + "вызывающий передал мусор вместо пути к данным");

        assertTrue(error.getMessage().contains("null"),
                "Сообщение об ошибке обязано называть причину — null вместо пути. Фактическое: "
                        + error.getMessage());
    }

    @Test
    void readClasspath_PathWithoutSlash_ReportsPathInsteadOfFailingOnSubstring() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> JsonResource.readClasspath("item-values-absent.json"),
                "Путь без каталога обязан отказать понятно: раньше он падал на вычислении каталога "
                        + "вместо сообщения, где искать файл");

        assertTrue(error.getMessage().contains("item-values-absent.json"),
                "Сообщение обязано называть весь путь 'item-values-absent.json': по нему находят "
                        + "потерянный файл. Фактическое: " + error.getMessage());
    }

    @Test
    void readClasspath_MissingFileWithFolders_ReportsResourceRoot() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> JsonResource.readClasspath("data/theminespirit/inventory/absent.json"),
                "Отсутствие файла обязано быть ошибкой с путём: без таблицы ценностей дух не решит, "
                        + "что дороже при переполнении инвентаря");

        assertTrue(error.getMessage().contains("data/theminespirit/inventory/absent.json"),
                "Сообщение обязано называть искомый путь: по нему находят потерянный файл. "
                        + "Фактическое: " + error.getMessage());
        assertTrue(error.getMessage().contains("src/main/resources"),
                "Сообщение обязано называть корень ресурсов: по нему видно, куда кладут файл данных. "
                        + "Фактическое: " + error.getMessage());
    }

    @Test
    void readClasspath_DataFileOfInventory_ReadsText() {
        String values = JsonResource.readClasspath("data/theminespirit/inventory/item_values.json");

        assertTrue(values.contains("rules"),
                "Файл ценностей читается с classpath и содержит список rules: без него таблица "
                        + "ценностей не строится. Прочитано " + values.length() + " символов");
    }

    @Test
    void parse_BrokenJson_ThrowsWithPath() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> JsonResource.parse("{\"rules\": [", "data/theminespirit/inventory/test.json"),
                "Битый JSON обязан ронять разбор: тихое «правил нет» сделало бы таблицу пустой");

        assertTrue(error.getMessage().contains("data/theminespirit/inventory/test.json"),
                "Сообщение обязано называть путь файла: по нему правят данные. Фактическое: "
                        + error.getMessage());
    }

    @Test
    void parse_RootArray_TellsThatObjectIsExpected() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> JsonResource.parse("[]", "data/theminespirit/inventory/test.json"),
                "Корень-массив обязан быть отказом: на верхнем уровне ждётся объект с полями");

        assertTrue(error.getMessage().contains("не объект"),
                "Сообщение обязано объяснять, что на верхнем уровне объект, а не массив. Фактическое: "
                        + error.getMessage());
    }

    @Test
    void items_Array_ReturnsEveryElement() {
        JsonNode root = JsonResource.parse("{\"list\": [1, 2, 3]}", "data/theminespirit/inventory/test.json");

        List<JsonNode> items = JsonResource.items(root.get("list"));

        assertEquals(3, items.size(),
                "items отдаёт все элементы списка: по ним загрузчик обходит правила и категории");
        assertEquals(3, items.get(2).asInt(),
                "Порядок элементов сохраняется: правило №2 обязано остаться вторым");
    }
}
