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
                "Порог по умолчанию — 90%: дух чинит предмет, когда его прочность не выше девяти десятых максимума, "
                        + "иначе ремонт слишком рано тратит запас или слишком поздно теряет предмет. При смене "
                        + "defaults проверь строку границ ниже в тесте");
    }

    @Test
    void parse_BoundaryPercent_AcceptsEdgesRejectsOutside() {
        assertEquals(
                new RepairData(1),
                RepairData.parse("""
                {"maxDurabilityPercent": 1}""", "test"),
                "1% — минимальный допустимый порог, иначе граница отвергается и настройка не грузится. При смене "
                        + "parse проверь строку 100 ниже в тесте");
        assertEquals(
                new RepairData(100),
                RepairData.parse("""
                {"maxDurabilityPercent": 100}""", "test"),
                "100% — максимальный допустимый порог, иначе граница отвергается и ремонт не настраивается. При "
                        + "смене parse проверь строки отказов ниже в тесте");

        assertThrows(
                IllegalStateException.class,
                () -> RepairData.parse("""
                {"maxDurabilityPercent": 0}""", "test"),
                "Нулевой порог чинил бы любой предмет: он обязан отклоняться, иначе ремонт тратит запас впустую. "
                        + "При смене parse проверь строку 101 ниже в тесте");
        assertThrows(
                IllegalStateException.class,
                () -> RepairData.parse("""
                {"maxDurabilityPercent": 101}""", "test"),
                "Порог выше 100% не чинил бы ничего: он обязан отклоняться, иначе предмет гниёт с полным запасом "
                        + "материала. При смене parse проверь строку пропуска ниже в тесте");
    }

    @Test
    void parse_MissingField_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> RepairData.parse("""
                {}""", "test"),
                "Файл без maxDurabilityPercent обязан отклоняться до любого поведения ремонта, иначе дефолт молча "
                        + "подставляет 90. При смене parse проверь строку имени ниже в тесте");

        assertTrue(
                error.getMessage().contains("maxDurabilityPercent"),
                "Пропуск поля обязан называть имя поля, иначе игрок не найдёт опечатку. Фактическое: "
                        + error.getMessage() + ". При смене текста проверь строку неизвестного ниже в тесте");
    }

    @Test
    void parse_UnknownField_Throws() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> RepairData.parse("""
                {"maxDurabilityPercent": 90, "anvil": true}""", "test"),
                "Файл с неизвестным полем обязан отклоняться до любого поведения ремонта, иначе опечатка тихо "
                        + "игнорируется. При смене parse проверь строку имени ниже в тесте");

        assertTrue(
                error.getMessage().contains("anvil"),
                "Неизвестное поле обязано отклоняться с его именем, иначе игрок не поймёт причину. Фактическое: "
                        + error.getMessage() + ". При смене текста проверь строку загрузки ниже в тесте");
    }

    @Test
    void load_ClasspathResource_MatchesDefaults() {
        assertEquals(
                RepairData.defaults(),
                RepairData.load(),
                "Файл " + RepairData.RESOURCE + " обязан совпадать с кодовыми DEFAULTS: расхождение тихо изменит, "
                        + "какую прочность дух считает достаточно высокой для ремонта, иначе баланс ломается. При "
                        + "смене RESOURCE проверь строку defaults выше в тесте");
    }
}
