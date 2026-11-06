package com.theminespirit.core.repair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RepairDataTests {

    @Test
    void defaults_MaxDurabilityPercent_IsNinety() {
        assertEquals(
                90,
                RepairData.defaults().maxDurabilityPercent(),
                "Порог по умолчанию — 90%: дух чинит предмет, когда его прочность не выше девяти десятых максимума");
    }

    @Test
    void parse_BoundaryPercent_AcceptsEdgesRejectsOutside() {
        assertEquals(new RepairData(1), RepairData.parse("""
                {"maxDurabilityPercent": 1}""", "test"), "1% — минимальный допустимый порог");
        assertEquals(new RepairData(100), RepairData.parse("""
                {"maxDurabilityPercent": 100}""", "test"), "100% — максимальный допустимый порог");

        assertThrows(
                IllegalStateException.class,
                () -> RepairData.parse("""
                {"maxDurabilityPercent": 0}""", "test"),
                "Нулевой порог чинил бы любой предмет: он обязан отклоняться");
        assertThrows(
                IllegalStateException.class,
                () -> RepairData.parse("""
                {"maxDurabilityPercent": 101}""", "test"),
                "Порог выше 100% не чинил бы ничего: он обязан отклоняться");
    }

    @Test
    void parse_MissingField_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> RepairData.parse("""
                {}""", "test"),
                "Файл без maxDurabilityPercent обязан отклоняться до любого поведения ремонта");

        assertTrue(
                error.getMessage().contains("maxDurabilityPercent"),
                "Пропуск поля обязан называть имя поля. Фактическое: " + error.getMessage());
    }

    @Test
    void parse_UnknownField_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> RepairData.parse("""
                {"maxDurabilityPercent": 90, "anvil": true}""", "test"),
                "Файл с неизвестным полем обязан отклоняться до любого поведения ремонта");

        assertTrue(
                error.getMessage().contains("anvil"),
                "Неизвестное поле обязано отклоняться с его именем. Фактическое: " + error.getMessage());
    }

    @Test
    void load_ClasspathResource_MatchesDefaults() {
        assertEquals(
                RepairData.defaults(),
                RepairData.load(),
                "Файл " + RepairData.RESOURCE + " обязан совпадать с кодовыми DEFAULTS: расхождение тихо "
                        + "изменит, какую прочность дух считает достаточно высокой для ремонта");
    }
}
