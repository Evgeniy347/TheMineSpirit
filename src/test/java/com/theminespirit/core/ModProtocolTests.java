package com.theminespirit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ModProtocolTests {
    @Test
    void matches_SameBuild_True() {
        ModProtocol server = new ModProtocol("0.1.0+b06538dea4198");
        ModProtocol client = new ModProtocol("  0.1.0+b06538dea4198  ");

        assertTrue(
                server.matches(client),
                "Одна и та же сборка (пробелы не в счёт) совпадает: иначе одинаковые клиент и сервер не пустят в игру");
    }

    @Test
    void matches_DifferentBuild_False() {
        ModProtocol server = new ModProtocol("0.1.0+b06538dea4198");
        ModProtocol client = new ModProtocol("0.1.0+3c1a31a3dbf1b");

        assertFalse(
                server.matches(client),
                "Разные jar при одном номере версии — разные сборки: хэш отличает их и не пускает старый клиент");
    }

    @Test
    void refusal_DifferentBuild_NamesBothAndAsksToUpdateClient() {
        String message = ModProtocol.refusal("0.1.0+3c1a31a3dbf1b", "0.1.0+b06538dea4198");

        assertTrue(
                message.contains("0.1.0+3c1a31a3dbf1b") && message.contains("0.1.0+b06538dea4198"),
                "Отказ называет обе сборки: по нему видно, что именно обновлять");
        assertTrue(
                message.contains("Обновите клиент"),
                "Отказ прямо просит обновить клиент: вместо краша set_entity_data игрок понимает, что делать");
    }

    @Test
    void build_Blank_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ModProtocol("   "),
                "Пустая строка сборки не описывает протокол: сверять было бы нечего");
    }

    @Test
    void build_Null_Throws() {
        assertThrows(
                NullPointerException.class,
                () -> new ModProtocol(null),
                "Без сборки протокол не сверить: null не должен молча стать «совпало»");
    }

    @Test
    void build_WithSurroundingSpaces_Stripped() {
        assertEquals(
                "dev",
                new ModProtocol(" dev ").build(),
                "Сборка хранится без пробелов: иначе клиент и сервер с одним jar не совпадут из-за форматирования");
    }
}
