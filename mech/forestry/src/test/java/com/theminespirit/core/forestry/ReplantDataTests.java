package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReplantDataTests {
    @Test
    void defaults_SquareRadiusStepGap_AreFourSixThreeTwo() {
        ReplantData defaults = ReplantData.defaults();

        assertEquals(
                4,
                defaults.squareSaplings(),
                "Квадрат 2x2 требует четыре саженца, иначе тёмный дуб и гигантская ель не вырастут. При смене "
                        + "defaults проверь радиус ниже в тесте");
        assertEquals(
                6,
                defaults.areaRadius(),
                "Радиус обновления участка по умолчанию — 6: у срубленного дерева обновляют поляну, а не весь лес, "
                        + "иначе дух засаживает весь биом. При смене defaults проверь шаг ниже в тесте");
        assertEquals(
                3,
                defaults.areaStep(),
                "Шаг досадки по умолчанию — 3: саженцы ставят реже, чем через клетку, чтобы не глушить друг друга, "
                        + "иначе лес не растёт. При смене defaults проверь зазор ниже в тесте");
        assertEquals(
                2,
                defaults.buildingGap(),
                "Зазор до построек по умолчанию — 2: саженец не встаёт вплотную к стене игрока, иначе дух портит "
                        + "стройку. При смене defaults проверь строку parse ниже в тесте");
    }

    @Test
    void parse_BoundaryValues_AcceptsEdgesRejectsOutside() {
        assertEquals(
                new ReplantData(4, 1, 1, 1),
                ReplantData.parse("""
                {"squareSaplings": 4, "areaRadius": 1, "areaStep": 1, "buildingGap": 1}""", "test"),
                "Наименьшие допустимые значения: 4 саженца, радиус и шаг 1, зазор 1, иначе граница отвергается и "
                        + "таблица не грузится. При смене ReplantData.parse проверь строку максимума ниже в тесте");
        assertEquals(
                new ReplantData(9, 32, 8, 8),
                ReplantData.parse("""
                {"squareSaplings": 9, "areaRadius": 32, "areaStep": 8, "buildingGap": 8}""", "test"),
                "Наибольшие допустимые значения: 9 саженцев, радиус 32, шаг 8, зазор 8, иначе граница отвергается и "
                        + "участок не засаживается. При смене parse проверь строки отказов ниже в тесте");

        assertThrows(
                IllegalStateException.class,
                () -> ReplantData.parse("""
                        {"squareSaplings": 3, "areaRadius": 6, "areaStep": 3, "buildingGap": 2}""", "test"),
                "Меньше четырёх саженцев на квадрат обязано отклоняться: квадрат 2x2 из трёх саженцев не собрать, "
                        + "иначе план обещает гигант. При смене parse проверь строку нулевого радиуса ниже в тесте");
        assertThrows(
                IllegalStateException.class,
                () -> ReplantData.parse("""
                        {"squareSaplings": 4, "areaRadius": 0, "areaStep": 3, "buildingGap": 2}""", "test"),
                "Нулевой радиус обязана отклоняться: досадки не будет, а таблица числится рабочей, иначе участок "
                        + "не засаживается. При смене parse проверь строку нулевого шага ниже в тесте");
        assertThrows(
                IllegalStateException.class,
                () -> ReplantData.parse("""
                        {"squareSaplings": 4, "areaRadius": 6, "areaStep": 0, "buildingGap": 2}""", "test"),
                "Нулевой шаг обязана отклоняться: одна и та же клетка повторялась бы бесконечно, иначе план "
                        + "зацикливается. При смене parse проверь строку нулевого зазора ниже в тесте");
        assertThrows(
                IllegalStateException.class,
                () -> ReplantData.parse("""
                        {"squareSaplings": 4, "areaRadius": 6, "areaStep": 3, "buildingGap": 0}""", "test"),
                "Нулевой зазор до построек обязана отклоняться: саженец встал бы вплотную к стене игрока, иначе "
                        + "политика построек нарушена. При смене parse проверь строку пропуска ниже в тесте");
    }

    @Test
    void parse_MissingField_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ReplantData.parse("""
                        {"areaRadius": 6, "areaStep": 3, "buildingGap": 2}""", "test"),
                "Файл без squareSaplings обязан отклоняться до любой пересадки, иначе дефолт молча подставляет "
                        + "четыре. При смене parse проверь строку имени ниже в тесте");

        assertTrue(
                error.getMessage().contains("squareSaplings"),
                "Пропуск squareSaplings обязан называть имя поля, иначе игрок не найдёт опечатку. Фактическое: "
                        + error.getMessage() + ". При смене текста ошибки проверь строку неизвестного поля ниже в "
                        + "тесте");
    }

    @Test
    void parse_UnknownField_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ReplantData.parse("""
                        {"squareSaplings": 4, "areaRadius": 6, "areaStep": 3, "buildingGap": 2, "seed": 7}""", "test"),
                "Файл с неизвестным полем обязан отклоняться до любой пересадки, иначе опечатка тихо игнорируется. "
                        + "При смене parse проверь строку имени ниже в тесте");

        assertTrue(
                error.getMessage().contains("неизвестное поле 'seed'"),
                "Неизвестное поле обязано отклоняться с его именем, иначе игрок не поймёт причину. Фактическое: "
                        + error.getMessage() + ". При смене текста ошибки проверь строку загрузки ниже в тесте");
    }

    @Test
    void load_ClasspathResource_MatchesDefaults() {
        assertEquals(
                ReplantData.defaults(),
                ReplantData.load(),
                "Файл " + ReplantData.RESOURCE + " обязан совпадать с кодовыми DEFAULTS: расхождение тихо поменяет, "
                        + "сколько саженцев дух тратит на квадрат и как далеко обновляет участок, иначе баланс "
                        + "ломается. При смене ReplantData.RESOURCE проверь строку defaults выше в тесте");
    }
}
