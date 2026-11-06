package com.theminespirit.core.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ProcessingRulesTests {
    private static final String VALID = """
            {"charcoalTarget": 16, "plankTarget": 32, "stickTarget": 32, "charcoalCount": 8,
             "cookCount": 4, "craftCount": 8, "baseDemand": 1.0, "demandScale": 8.0, "stationRadius": 16}
            """;

    @Test
    void parse_ValidJson_MatchesDefaults() {
        assertEquals(
                ProcessingRules.defaults(),
                ProcessingRules.parse(VALID, "processing.json"),
                "Файл данных обязан совпадать с умолчаниями: иначе ядро и адаптер расходятся в числах");
    }

    @Test
    void load_ClasspathResource_Reads() {
        assertEquals(
                ProcessingRules.defaults(),
                ProcessingRules.load(),
                "Файл data/theminespirit/processing/processing.json обязан читаться: цели и коэффициенты "
                        + "переработки — данные, а не ветки в коде");
    }

    @Test
    void parse_UnknownField_Throws() {
        String json = VALID.replace("\"demandScale\": 8.0", "\"demandScale\": 8.0, \"вкус\": 1");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> ProcessingRules.parse(json, "processing.json"),
                "Неизвестное поле ломает загрузку: опечатка в данных не должна молча ложиться в умолчания");

        assertEquals(
                true, error.getMessage().contains("вкус"), "Сообщение называет лишнее поле: " + error.getMessage());
    }

    @Test
    void parse_NegativeTarget_Throws() {
        assertThrows(
                IllegalStateException.class,
                () -> ProcessingRules.parse(
                        VALID.replace("\"plankTarget\": 32", "\"plankTarget\": -1"), "processing.json"),
                "Отрицательная цель запаса отвергается: дефицит выходил бы за границы и ломал спрос");
    }

    @Test
    void parse_CountOutOfRange_Throws() {
        assertThrows(
                IllegalStateException.class,
                () -> ProcessingRules.parse(VALID.replace("\"cookCount\": 4", "\"cookCount\": 0"), "processing.json"),
                "Нулевое число предметов за запуск отвергается: скрипт отказал бы на плане без работы");
    }
}
