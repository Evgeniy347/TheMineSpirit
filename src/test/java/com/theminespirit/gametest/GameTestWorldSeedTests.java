package com.theminespirit.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated
class GameTestWorldSeedTests {
    @Test
    void resolve_WithoutProperty_IsFixedAndRepeatable() {
        String previous = System.getProperty(GameTestWorldSeed.PROPERTY);
        try {
            System.clearProperty(GameTestWorldSeed.PROPERTY);
            long first = GameTestWorldSeed.resolve();
            long second = GameTestWorldSeed.resolve();
            assertEquals(
                    first,
                    second,
                    "Сид мира GameTest без --seed обязан быть постоянным: от него зависит место сетки тестов, "
                            + "а случайное место двигает сцены по чужим чанкам и делает полный прогон флейком "
                            + "(route_sanddig_layersflatspiritbringssand, craftscene_arenasteps_allpass). "
                            + "Проверь GameTestWorldSeed.resolve и GameTestSeeding.onServerStarted");
        } finally {
            restore(previous);
        }
    }

    @Test
    void resolve_ExplicitProperty_IsParsed() {
        String previous = System.getProperty(GameTestWorldSeed.PROPERTY);
        try {
            System.setProperty(GameTestWorldSeed.PROPERTY, "42");
            assertEquals(
                    42L,
                    GameTestWorldSeed.resolve(),
                    "Явный --seed обязан задавать сид мира: иначе повтор прогона с тем же --seed не повторит "
                            + "место сетки тестов. Проверь разбор theminespirit.gametest.seed");
        } finally {
            restore(previous);
        }
    }

    private static void restore(String previous) {
        if (previous == null) {
            System.clearProperty(GameTestWorldSeed.PROPERTY);
        } else {
            System.setProperty(GameTestWorldSeed.PROPERTY, previous);
        }
    }
}
