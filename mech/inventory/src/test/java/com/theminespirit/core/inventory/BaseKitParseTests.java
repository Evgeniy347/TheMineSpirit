package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BaseKitParseTests {
    private static final String ORIGIN = "data/theminespirit/inventory/base_kit.json";

    @Test
    void parse_RuleWithBothMatchAndCategory_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> BaseKit.parse("""
                        {"rules": [{"match": ["*_sword"], "category": "tools", "keep": 1, "mode": "best"}]}
                        """, ORIGIN),
                "Правило с match и category сразу обязано быть ошибкой: иначе два способа выбора предметов "
                        + "делают квоту неопределённой и разгрузка станет непредсказуемой. "
                        + "При смене parse проверь правило с обоими полями");

        assertTrue(
                error.getMessage().contains("match") && error.getMessage().contains("category"),
                "Сообщение обязано называть оба поля, иначе правило не исправить; фактическое: " + error.getMessage()
                        + ". При смене текста проверь match и category");
    }

    @Test
    void parse_RuleWithoutMatchAndCategory_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> BaseKit.parse("""
                        {"rules": [{"keep": 1, "mode": "best"}]}
                        """, ORIGIN),
                "Правило без шаблонов и без категории обязано быть ошибкой: иначе оно молча ничего "
                        + "не описывает и предмет уходит в контейнер мимо набора. "
                        + "При смене parse проверь правило без полей выбора");

        assertTrue(
                error.getMessage().contains("match"),
                "Сообщение обязано называть поле match, иначе правило не исправить; фактическое: " + error.getMessage()
                        + ". При смене текста проверь имя поля match в причине");
    }

    @Test
    void parse_NegativeKeep_ThrowsWithValue() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> BaseKit.parse("""
                        {"rules": [{"match": ["torch"], "keep": -1, "mode": "any"}]}
                        """, ORIGIN),
                "Отрицательная квота обязана быть ошибкой: иначе квота «сколько оставить» меньше нуля "
                        + "отдала бы в контейнер даже предметы, которых у духа нет. "
                        + "При смене parse проверь keep = -1");

        assertTrue(
                error.getMessage().contains("-1"),
                "Сообщение обязано называть отрицательную квоту -1, иначе строку файла не найти; фактическое: "
                        + error.getMessage() + ". При смене текста проверь значение квоты");
    }

    @Test
    void parse_UnknownMode_ThrowsWithValue() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> BaseKit.parse("""
                        {"rules": [{"match": ["torch"], "keep": 1, "mode": "cheapest"}]}
                        """, ORIGIN),
                "Неизвестный режим обязан быть ошибкой: иначе опечатка в режиме делает квоту неработающей, "
                        + "а молчаливый пропуск отдал бы весь набор в контейнер. "
                        + "При смене parse проверь mode = cheapest");

        assertTrue(
                error.getMessage().contains("cheapest"),
                "Сообщение обязано называть неверный режим, иначе файл не исправить; фактическое: " + error.getMessage()
                        + ". При смене текста проверь значение режима");
    }

    @Test
    void parse_PatternOutsideFilterAlphabet_ThrowsWithPattern() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> BaseKit.parse("""
                        {"rules": [{"match": ["*_Sword"], "keep": 1, "mode": "best"}]}
                        """, ORIGIN),
                "Шаблон вне алфавита IdFilter обязан быть ошибкой: иначе он не совпадёт ни с одним id "
                        + "и тихо выключит правило набора. При смене parse проверь шаблон *_Sword");

        assertTrue(
                error.getMessage().contains("*_Sword"),
                "Сообщение обязано называть плохой шаблон, иначе файл не исправить; фактическое: " + error.getMessage()
                        + ". При смене текста проверь шаблон в причине");
    }

    @Test
    void load_MissingFile_ThrowsWithPath() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> BaseKit.load("data/theminespirit/inventory/absent-kit.json"),
                "Отсутствие файла базового набора обязано быть ошибкой: иначе без набора разгрузка "
                        + "отдала бы в контейнер всё, включая последний лук. "
                        + "При смене load проверь отсутствующий файл");

        assertTrue(
                error.getMessage().contains("data/theminespirit/inventory/absent-kit.json"),
                "Сообщение обязано называть путь, по которому файл искали, иначе потерянный файл не найти; "
                        + "фактическое: " + error.getMessage() + ". При смене текста проверь путь набора");
    }
}
