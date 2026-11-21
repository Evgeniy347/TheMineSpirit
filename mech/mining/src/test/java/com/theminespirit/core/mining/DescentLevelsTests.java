package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DescentLevelsTests {

    @Test
    void defaults_WidenThreeBySixAndStairTwo_AreLiterals() {
        DescentLevels defaults = DescentLevels.defaults();

        assertEquals(
                3,
                defaults.widenWidth(),
                "Должная ширина расширения — 3 блока (решение владельца 15.10): факт зафиксирован литералом"
                        + " Иначе тест defaults_WidenThreeBySixAndStairTwo_AreLiterals перестанет ловить эту поломку. При смене defaults_WidenThreeBySixAndStairTwo_AreLiterals проверь это утверждение.");
        assertEquals(
                6,
                defaults.widenHeight(),
                "Должная высота расширения — 6 блоков (решение владельца 15.10): факт зафиксирован литералом"
                        + " Иначе тест defaults_WidenThreeBySixAndStairTwo_AreLiterals перестанет ловить эту поломку. При смене defaults_WidenThreeBySixAndStairTwo_AreLiterals проверь это утверждение.");
        assertEquals(
                2,
                defaults.stairWidth(),
                "Должная ширина ступенек — 2 блока: третий оставляют под будущие рельсы"
                        + " Иначе тест defaults_WidenThreeBySixAndStairTwo_AreLiterals перестанет ловить эту поломку. При смене defaults_WidenThreeBySixAndStairTwo_AreLiterals проверь это утверждение.");
    }

    @Test
    void parse_ValidJson_ReturnsValues() {
        DescentLevels data = DescentLevels.parse("""
                {"widenWidth": 5, "widenHeight": 4, "stairWidth": 3}""", "test");

        assertEquals(
                new DescentLevels(5, 4, 3),
                data,
                "Корректный файл обязан разбираться в значения без потерь"
                        + " Иначе тест parse_ValidJson_ReturnsValues перестанет ловить эту поломку. При смене parse_ValidJson_ReturnsValues проверь это утверждение.");
    }

    @Test
    void parse_EvenWidth_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> DescentLevels.parse("""
                        {"widenWidth": 4, "widenHeight": 6, "stairWidth": 2}""", "test"),
                "Чётная ширина расширения обязана отклоняться: проход симметричен центральной колонке"
                        + " Иначе тест parse_EvenWidth_Throws перестанет ловить эту поломку. При смене parse_EvenWidth_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("Ширина расширения"),
                "Отказ обязан называть ширину расширения"
                        + " Иначе тест parse_EvenWidth_Throws перестанет ловить эту поломку. При смене parse_EvenWidth_Throws проверь это утверждение.");
    }

    @Test
    void parse_StairWidthNotLessThanWidth_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> DescentLevels.parse("""
                        {"widenWidth": 3, "widenHeight": 6, "stairWidth": 3}""", "test"),
                "Ступеньки на всех полосах обязаны отклоняться: третью оставляют под рельсы"
                        + " Иначе тест parse_StairWidthNotLessThanWidth_Throws перестанет ловить эту поломку. При смене parse_StairWidthNotLessThanWidth_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("Ширина ступенек"),
                "Отказ обязан называть ширину ступенек"
                        + " Иначе тест parse_StairWidthNotLessThanWidth_Throws перестанет ловить эту поломку. При смене parse_StairWidthNotLessThanWidth_Throws проверь это утверждение.");
    }

    @Test
    void parse_HeightLessThanOne_Throws() {
        assertThrows(
                IllegalStateException.class,
                () -> DescentLevels.parse("""
                        {"widenWidth": 3, "widenHeight": 0, "stairWidth": 2}""", "test"),
                "Нулевая высота расширения обязана отклоняться: расширять нечем"
                        + " Иначе тест parse_HeightLessThanOne_Throws перестанет ловить эту поломку. При смене parse_HeightLessThanOne_Throws проверь это утверждение.");
    }

    @Test
    void parse_MissingField_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> DescentLevels.parse("""
                        {"widenHeight": 6, "stairWidth": 2}""", "test"),
                "Файл без widenWidth обязан отклоняться до любого расширения"
                        + " Иначе тест parse_MissingField_Throws перестанет ловить эту поломку. При смене parse_MissingField_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("widenWidth"),
                "Пропуск поля обязан называть его имя: иначе ошибка выглядит как поломка мода"
                        + " При смене parse_MissingField_Throws проверь это утверждение.");
    }

    @Test
    void parse_UnknownField_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> DescentLevels.parse("""
                        {"widenWidth": 3, "widenHeight": 6, "stairWidth": 2, "depth": 10}""", "test"),
                "Файл с неизвестным полем обязан отклоняться до любого расширения"
                        + " Иначе тест parse_UnknownField_Throws перестанет ловить эту поломку. При смене parse_UnknownField_Throws проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("неизвестное поле 'depth'"),
                "Неизвестное поле обязано отклоняться с его именем: опечатка не должна молча игнорироваться"
                        + " Иначе тест parse_UnknownField_Throws перестанет ловить эту поломку. При смене parse_UnknownField_Throws проверь это утверждение.");
    }

    @Test
    void load_ClasspathResource_MatchesDefaults() {
        assertEquals(
                DescentLevels.defaults(),
                DescentLevels.load(),
                "Файл " + DescentLevels.RESOURCE + " обязан совпадать с кодовыми DEFAULTS: расхождение тихо "
                        + "изменит ширину и высоту шахты у игроков"
                        + " Иначе тест load_ClasspathResource_MatchesDefaults перестанет ловить эту поломку. При смене load_ClasspathResource_MatchesDefaults проверь это утверждение.");
    }
}
