package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SayParamsTests {
    @Test
    void from_TextPresent_KeepsText() {
        SayParams params = SayParams.from(Map.of("text", "Стройка ждёт"));

        assertEquals("Стройка ждёт", params.text(),
                "say обязан сохранить текст дословно до нормализации: искажение на маппинге уйдёт в чат игрокам");
    }

    @Test
    void from_MissingText_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SayParams.from(Map.of()),
                "say без текста обязан отказывать: отправлять в чат нечего, а пустая строка — шум");

        assertTrue(error.getMessage().contains("text"),
                "Отказ обязан называть недостающий параметр 'text'. Фактическое: " + error.getMessage());
    }

    @Test
    void from_BlankText_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SayParams.from(Map.of("text", "   ")),
                "say с текстом из пробелов обязан отказывать: после нормализации получилась бы пустая строка");
    }

    @Test
    void from_NonStringText_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SayParams.from(Map.of("text", 42)),
                "say с нестроковым текстом обязан отказывать: число не превращается в речь молча");

        assertTrue(error.getMessage().contains("text"),
                "Отказ обязан называть параметр 'text'. Фактическое: " + error.getMessage());
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(NullPointerException.class, () -> SayParams.from(null),
                "SayParams.from(null) обязан отказывать: без карты параметров маппинг только читает план");
    }
}
