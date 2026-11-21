package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ValueTableParseTests {
    private static final String ORIGIN = "data/theminespirit/inventory/item_values.json";

    @Test
    void parse_BrokenJson_ThrowsWithPathAndReason() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ValueTable.parse("{\"default\": 1, ", ORIGIN),
                "Битый файл ценностей обязан ронять загрузку: иначе тихое «все предметы по рублю» сделало бы "
                        + "выбор при переполнении инвентаря случайным. При смене parse проверь обрезанный JSON");

        assertTrue(
                error.getMessage().contains(ORIGIN),
                "Сообщение об ошибке обязано называть путь к файлу, иначе автор не найдёт, какой файл править; "
                        + "фактическое: " + error.getMessage() + ". При смене текста проверь путь таблицы");
        assertTrue(
                error.getMessage().contains("JSON"),
                "Сообщение об ошибке обязано называть причину — неразобранный JSON, иначе дефект не найти; "
                        + "фактическое: " + error.getMessage() + ". При смене текста проверь слово JSON");
    }

    @Test
    void parse_UnknownField_ThrowsWithFieldName() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ValueTable.parse("""
                        {"default": 1, "valu": 5,
                         "rules": [{"match": ["stone"], "value": 1}],
                         "categories": [{"name": "misc", "match": ["*"]}]}
                        """, ORIGIN),
                "Опечатка в имени поля обязана быть ошибкой: иначе правило с полем valu не читается молча, "
                        + "а тихий пропуск сделал бы таблицу неполной. При смене parse проверь поле valu");

        assertTrue(
                error.getMessage().contains("valu"),
                "Сообщение обязано называть неизвестное поле, иначе файл не исправить; фактическое: "
                        + error.getMessage() + ". При смене текста проверь имя поля в причине");
    }

    @Test
    void parse_PatternOutsideFilterAlphabet_ThrowsWithPattern() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ValueTable.parse("""
                        {"default": 1,
                         "rules": [{"match": ["Iron_Ingot"], "value": 1}],
                         "categories": [{"name": "misc", "match": ["*"]}]}
                        """, ORIGIN),
                "Шаблон вне алфавита IdFilter обязан быть ошибкой: иначе такой шаблон не совпадёт ни с одним "
                        + "id и тихо выключит правило. При смене parse проверь шаблон Iron_Ingot");

        assertTrue(
                error.getMessage().contains("Iron_Ingot"),
                "Сообщение обязано называть плохой шаблон, иначе файл не исправить; фактическое: " + error.getMessage()
                        + ". При смене текста проверь шаблон в причине");
    }

    @Test
    void parse_NegativeValue_ThrowsWithValue() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ValueTable.parse("""
                        {"default": 1,
                         "rules": [{"match": ["*_ingot"], "value": -5}],
                         "categories": [{"name": "misc", "match": ["*"]}]}
                        """, ORIGIN),
                "Отрицательная ценность обязана быть ошибкой: иначе «самое дешёвое» с отрицательными ценами "
                        + "выбирает предмет, которого нет в инвентаре. При смене parse проверь value = -5");

        assertTrue(
                error.getMessage().contains("-5"),
                "Сообщение обязано называть отрицательное значение, иначе строку файла не найти; фактическое: "
                        + error.getMessage() + ". При смене текста проверь значение цены");
    }

    @Test
    void parse_DeadRule_ThrowsWithRuleNumber() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ValueTable.parse("""
                        {"default": 1,
                         "rules": [{"match": ["*_ingot"], "value": 5},
                                   {"match": ["iron_ingot"], "value": 40}],
                         "categories": [{"name": "misc", "match": ["*"]}]}
                        """, ORIGIN),
                "Правило, перекрытое предыдущим целиком, никогда не сработает: оно обязано быть ошибкой файла, "
                        + "иначе цена предмета расходится с ценой в правилах выше. При смене parse проверь мёртвое правило");

        assertTrue(
                error.getMessage().contains("№1"),
                "Сообщение обязано называть мёртвое правило №1, иначе строку файла не найти; фактическое: "
                        + error.getMessage() + ". При смене текста проверь номер правила");
    }

    @Test
    void parse_EmptyRules_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ValueTable.parse("""
                        {"default": 1, "rules": [],
                         "categories": [{"name": "misc", "match": ["*"]}]}
                        """, ORIGIN),
                "Файл без правил обязан быть ошибкой: иначе все предметы получают default и таблица теряет "
                        + "смысл. При смене parse проверь пустой список rules");

        assertTrue(
                error.getMessage().contains("rules"),
                "Сообщение обязано называть отсутствующий список rules, иначе файл не исправить; фактическое: "
                        + error.getMessage() + ". При смене текста проверь имя списка");
    }

    @Test
    void parse_LastCategoryWithoutCatchAll_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ValueTable.parse("""
                        {"default": 1,
                         "rules": [{"match": ["*_ingot"], "value": 1}],
                         "categories": [{"name": "materials", "match": ["*_ingot"]}]}
                        """, ORIGIN),
                "Последняя категория без шаблона '*' обязана быть ошибкой: иначе предмет вне категорий не получил "
                        + "бы места при сортировке сундука и сортёр упал бы уже в игре. При смене parse проверь ловушку '*'");

        assertTrue(
                error.getMessage().contains("materials"),
                "Сообщение обязано называть последнюю категорию, иначе файл не исправить; фактическое: "
                        + error.getMessage() + ". При смене текста проверь имя категории");
    }

    @Test
    void load_MissingFile_ThrowsWithPath() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ValueTable.load("data/theminespirit/inventory/absent.json"),
                "Отсутствие файла ценностей на classpath обязано быть ошибкой: иначе дух не решит, что дороже "
                        + "при переполнении инвентаря. При смене load проверь отсутствующий файл");

        assertTrue(
                error.getMessage().contains("data/theminespirit/inventory/absent.json"),
                "Сообщение обязано называть путь, по которому файл искали, иначе потерянный ресурс не найти; "
                        + "фактическое: " + error.getMessage() + ". При смене текста проверь путь таблицы");
    }
}
