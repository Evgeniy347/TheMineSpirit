package com.theminespirit.core.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FishingDataTests {

    @Test
    void defaults_MinWidthAndDepth_AreTwoAndOne() {
        FishingData defaults = FishingData.defaults();

        assertEquals(2, defaults.minWidth(),
                "Должная наименьшая сторона открытой воды — 2: удочку забрасывают в пятно минимум 2x2");
        assertEquals(1, defaults.minDepth(),
                "Должная наименьшая глубина — 1: на воде толщиной в один блок рыба уже водится");
    }

    @Test
    void parse_BoundaryWater_AcceptsEdgesRejectsOutside() {
        assertEquals(new FishingData(2, 1), FishingData.parse("""
                {"minWidth": 2, "minDepth": 1}""", "test"),
                "Наименьшая допустимая вода — 2x2 глубиной 1");
        assertEquals(new FishingData(8, 8), FishingData.parse("""
                {"minWidth": 8, "minDepth": 8}""", "test"),
                "Наибольшая допустимая вода — 8x8 глубиной 8");

        assertThrows(IllegalStateException.class,
                () -> FishingData.parse("""
                        {"minWidth": 1, "minDepth": 1}""", "test"),
                "Вода уже 2x2 обязана отклоняться: в одну клетку удочку не забросить");
        assertThrows(IllegalStateException.class,
                () -> FishingData.parse("""
                        {"minWidth": 9, "minDepth": 1}""", "test"),
                "Вода шире 8x8 обязана отклоняться: это уже море, а не пруд у базы");
        assertThrows(IllegalStateException.class,
                () -> FishingData.parse("""
                        {"minWidth": 2, "minDepth": 0}""", "test"),
                "Нулевая глубина обязана отклоняться: мокрый пол не водоём");
        assertThrows(IllegalStateException.class,
                () -> FishingData.parse("""
                        {"minWidth": 2, "minDepth": 9}""", "test"),
                "Глубина больше 8 обязана отклоняться: пруд глубже не бывает");
    }

    @Test
    void parse_MissingField_Throws() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> FishingData.parse("""
                        {"minDepth": 1}""", "test"),
                "Файл без minWidth обязан отклоняться до любого поведения ловли");

        assertTrue(error.getMessage().contains("minWidth"),
                "Пропуск minWidth обязан называть имя поля. Фактическое: " + error.getMessage());
    }

    @Test
    void parse_UnknownField_Throws() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> FishingData.parse("""
                        {"minWidth": 2, "minDepth": 1, "lureSpeed": 3}""", "test"),
                "Файл с неизвестным полем обязан отклоняться до любого поведения ловли");

        assertTrue(error.getMessage().contains("неизвестное поле 'lureSpeed'"),
                "Неизвестное поле обязано отклоняться с его именем. Фактическое: " + error.getMessage());
    }

    @Test
    void load_ClasspathResource_MatchesDefaults() {
        assertEquals(FishingData.defaults(), FishingData.load(),
                "Файл " + FishingData.RESOURCE + " обязан совпадать с кодовыми DEFAULTS: расхождение тихо "
                        + "изменит, какую воду дух считает пригодной для рыбалки");
    }
}
