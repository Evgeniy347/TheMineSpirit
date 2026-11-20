package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.api.JsonResource;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonResourceTests {
    @Test
    void readClasspath_NullPath_FailsFastOnContract() {
        NullPointerException error = assertThrows(
                NullPointerException.class,
                () -> JsonResource.readClasspath(null),
                "Путь null обязан падать на контракте, а не молча искать файл без имени: вызывающий передал "
                        + "мусор вместо пути к данным, иначе дефект всплывёт далеко от места вызова. "
                        + "При смене readClasspath проверь null");

        assertTrue(
                error.getMessage().contains("null"),
                "Сообщение об ошибке обязано называть причину — null вместо пути, иначе дефект не найти. Фактическое: "
                        + error.getMessage() + ". При смене текста проверь слово null");
    }

    @Test
    void readClasspath_PathWithoutSlash_ReportsPathInsteadOfFailingOnSubstring() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> JsonResource.readClasspath("item-values-absent.json"),
                "Путь без каталога обязан отказать понятно: раньше он падал на вычислении каталога "
                        + "вместо сообщения, где искать файл, иначе дефект не найти. "
                        + "При смене readClasspath проверь путь без слэша");

        assertTrue(
                error.getMessage().contains("item-values-absent.json"),
                "Сообщение обязано называть весь путь 'item-values-absent.json': по нему находят "
                        + "потерянный файл, иначе файл ищут не там. Фактическое: " + error.getMessage()
                        + ". При смене текста проверь полное имя файла");
    }

    @Test
    void readClasspath_MissingFileWithFolders_ReportsResourceRoot() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> JsonResource.readClasspath("data/theminespirit/inventory/absent.json"),
                "Отсутствие файла обязано быть ошибкой с путём: без таблицы ценностей дух не решит, "
                        + "что дороже при переполнении инвентаря, иначе дроп выбирается наугад. "
                        + "При смене readClasspath проверь отсутствующий файл");

        assertTrue(
                error.getMessage().contains("data/theminespirit/inventory/absent.json"),
                "Сообщение обязано называть искомый путь: по нему находят потерянный файл, иначе путь "
                        + "в отчёте не указать. Фактическое: "
                        + error.getMessage() + ". При смене текста проверь полный путь данных");
        assertTrue(
                error.getMessage().contains("src/main/resources"),
                "Сообщение обязано называть корень ресурсов: по нему видно, куда кладут файл данных, "
                        + "иначе файл ищут в пустом каталоге. Фактическое: "
                        + error.getMessage() + ". При смене текста проверь src/main/resources");
    }

    @Test
    void readClasspath_DataFileOfInventory_ReadsText() {
        String values = JsonResource.readClasspath("data/theminespirit/inventory/item_values.json");

        assertTrue(
                values.contains("rules"),
                "Файл ценностей читается с classpath и содержит список rules: иначе таблица ценностей "
                        + "не строится и дроп не выбрать. Прочитано " + values.length()
                        + " символов. При смене JsonResource проверь item_values.json");
    }

    @Test
    void parse_BrokenJson_ThrowsWithPath() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> JsonResource.parse("{\"rules\": [", "data/theminespirit/inventory/test.json"),
                "Битый JSON обязан ронять разбор: тихое «правил нет» сделало бы таблицу пустой и защита "
                        + "перестала бы работать, иначе дух выбросит нужное. При смене parse проверь обрезанный JSON");

        assertTrue(
                error.getMessage().contains("data/theminespirit/inventory/test.json"),
                "Сообщение обязано называть путь файла: по нему правят данные, иначе дефект не найти. "
                        + "Фактическое: " + error.getMessage()
                        + ". При смене текста проверь путь data/theminespirit/inventory/test.json");
    }

    @Test
    void parse_RootArray_TellsThatObjectIsExpected() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> JsonResource.parse("[]", "data/theminespirit/inventory/test.json"),
                "Корень-массив обязан быть отказом: на верхнем уровне ждётся объект с полями, иначе разбор "
                        + "падает непонятно. При смене parse проверь корень []");

        assertTrue(
                error.getMessage().contains("не объект"),
                "Сообщение обязано объяснять, что на верхнем уровне объект, а не массив, иначе разбор "
                        + "падает непонятно. Фактическое: "
                        + error.getMessage() + ". При смене текста проверь «не объект»");
    }

    @Test
    void items_Array_ReturnsEveryElement() {
        JsonNode root = JsonResource.parse("{\"list\": [1, 2, 3]}", "data/theminespirit/inventory/test.json");

        List<JsonNode> items = JsonResource.items(root.get("list"));

        assertEquals(
                3,
                items.size(),
                "items отдаёт все элементы списка: по ним загрузчик обходит правила и категории, иначе "
                        + "потеря элемента оставит правило без проверки и ценности посчитаются неверно. "
                        + "При смене items проверь список из трёх");
        assertEquals(
                3,
                items.get(2).asInt(),
                "Порядок элементов сохраняется: правило номер два обязано остаться вторым, иначе приоритет "
                        + "шаблонов перевернётся и предмет попадёт не в свой сундук. При смене items проверь порядок");
    }
}
