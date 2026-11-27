package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DigOutRouteTests {
    @Test
    void tick_AscentRouteMissing_RetriesBeforeManualFallback() throws IOException {
        String source = Files.readString(Path.of("mod/src/main/java/com/theminespirit/neoforge/DigOutRoute.java"));
        String compact = source.replaceAll("\\s+", "");

        assertTrue(
                compact.contains("if(route==null&&retries<RETRIES&&!routeFailed){returnState.RUNNING;}"),
                "Подъём повторяет поиск до лимита, прежде чем перейти к ручным ступеням: иначе первый "
                        + "временный NO_ROUTE снова превращается в кружение вокруг поля. При смене "
                        + "tick_AscentRouteMissing_RetriesBeforeManualFallback проверь DigOutRoute.tick и spirit-6.");
    }

    @Test
    void mode_SpiritBobsWhileFollowingRoute_RouteKept() {
        assertFalse(
                DigOutRoute.mode(70, 71, false, true),
                "Дух на ступеньке качается между y=70 и 71 у дома на y=70, путь уже идёт — режим не переключается: "
                        + "иначе готовый путь отменялся и искался заново по кругу (сервер 12.11, поиски до 85 с)"
                        + " При смене mode_SpiritBobsWhileFollowingRoute_RouteKept проверь это утверждение.");
    }

    @Test
    void mode_NoRoute_DecidedByHeight() {
        assertTrue(
                DigOutRoute.mode(70, 71, false, false),
                "Пути нет, дом ниже — новый путь вниз"
                        + " Иначе тест mode_NoRoute_DecidedByHeight перестанет ловить эту поломку. При смене mode_NoRoute_DecidedByHeight проверь это утверждение.");
        assertFalse(
                DigOutRoute.mode(71, 70, true, false),
                "Пути нет, дом не ниже — новый путь к дому"
                        + " Иначе тест mode_NoRoute_DecidedByHeight перестанет ловить эту поломку. При смене mode_NoRoute_DecidedByHeight проверь это утверждение.");
    }
}
