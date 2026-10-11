package com.theminespirit.core.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChunkLoadingRadiusTests {
    @Test
    void effective_ConfiguredPositive_ReturnsConfigured() {
        assertEquals(7, ChunkLoadingRadius.effective(7, 3),
                "Явный радиус из конфига обязан использоваться как есть: иначе настройка молча не действует, "
                        + "и дух держит не ту зону, которую просил игрок");
    }

    @Test
    void effective_Auto_ReturnsSimulationDistance() {
        assertEquals(10, ChunkLoadingRadius.effective(ChunkLoadingRadius.AUTO, 10),
                "При -1 радиус обязан равняться simulation distance сервера: дух держит столько же, сколько игрок");
    }

    @Test
    void effective_AutoWithZeroSimulationDistance_ReturnsMinimum() {
        assertEquals(1, ChunkLoadingRadius.effective(ChunkLoadingRadius.AUTO, 0),
                "Нулевая simulation distance (тестовый сервер) не должна давать радиус 0: при R=0 переход в соседний "
                        + "чанк оставляет духа без тикающего чанка, и он замирает навсегда");
    }

    @Test
    void effective_ConfiguredBelowMinimum_ReturnsMinimum() {
        assertEquals(1, ChunkLoadingRadius.effective(0, 0),
                "Радиус ниже минимума обязан подниматься до 1: иначе квадрат вырождается и дух застревает на границе");
    }
}
