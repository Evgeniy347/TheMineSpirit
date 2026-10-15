package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DescentDataTests {

    @Test
    void defaults_TargetYAndTorchInterval_AreMinus58AndFour() {
        DescentData defaults = DescentData.defaults();

        assertEquals(-58, defaults.targetY(),
                "Должный целевой этаж — -58: уровень алмазов и ниже дна построек, факт зафиксирован литералом");
        assertEquals(4, defaults.torchInterval(),
                "Должный интервал факелов — 4: через четыре шага, факт зафиксирован литералом");
    }

    @Test
    void parse_WorldBoundLiterals_AreMinus64And319AndTorchOneAndSixteen() {
        DescentData bottom = DescentData.parse("""
                {"targetY": -64, "torchInterval": 1}""", "test");
        DescentData top = DescentData.parse("""
                {"targetY": 319, "torchInterval": 16}""", "test");

        assertEquals(new DescentData(-64, 1), bottom, "Дно мира и частый интервал — допустимая граница данных");
        assertEquals(new DescentData(319, 16), top, "Потолок мира и редкий интервал — допустимая граница данных");

        assertThrows(IllegalStateException.class,
                () -> DescentData.parse("""
                        {"targetY": -65, "torchInterval": 4}""", "test"),
                "Цель под дном мира обязана отклоняться");
        assertThrows(IllegalStateException.class,
                () -> DescentData.parse("""
                        {"targetY": 320, "torchInterval": 4}""", "test"),
                "Цель выше потолка мира обязана отклоняться");
        assertThrows(IllegalStateException.class,
                () -> DescentData.parse("""
                        {"targetY": -58, "torchInterval": 0}""", "test"),
                "Нулевой интервал факелов обязан отклоняться");
        assertThrows(IllegalStateException.class,
                () -> DescentData.parse("""
                        {"targetY": -58, "torchInterval": 17}""", "test"),
                "Интервал факелов больше 16 обязан отклоняться");
    }

    @Test
    void parse_ValidJson_ReturnsValues() {
        DescentData data = DescentData.parse("""
                {"targetY": -30, "torchInterval": 8}""", "test");

        assertEquals(new DescentData(-30, 8), data, "Корректный файл обязан разбираться в значения без потерь");
    }

    @Test
    void parse_MissingTargetY_Throws() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DescentData.parse("""
                        {"torchInterval": 4}""", "test"),
                "Файл без targetY обязан отклоняться до любого поведения спуска");

        assertTrue(error.getMessage().contains("targetY"),
                "Пропуск targetY обязан называть имя поля: иначе ошибка выглядит как поломка мода");
    }

    @Test
    void parse_UnknownField_Throws() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DescentData.parse("""
                        {"targetY": -58, "torchInterval": 4, "depth": 10}""", "test"),
                "Файл с неизвестным полем обязан отклоняться до любого поведения спуска");

        assertTrue(error.getMessage().contains("неизвестное поле 'depth'"),
                "Неизвестное поле обязано отклоняться с его именем: опечатка не должна молча игнорироваться");
    }

    @Test
    void parse_BrokenJson_Throws() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DescentData.parse("not a json", "test"),
                "Синтаксически битый файл обязан отклоняться с внятной ошибкой");

        assertTrue(error.getMessage().contains("не разобран JSON"),
                "Синтаксическая ошибка JSON обязана называться явно");
    }

    @Test
    void load_ClasspathResource_MatchesDefaults() {
        assertEquals(DescentData.defaults(), DescentData.load(),
                "Файл " + DescentData.RESOURCE + " обязан совпадать с кодовыми DEFAULTS: расхождение тихо "
                        + "изменит поведение спуска у игроков");
    }
}
