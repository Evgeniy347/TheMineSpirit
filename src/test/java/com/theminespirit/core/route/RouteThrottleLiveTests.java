package com.theminespirit.core.route;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RouteThrottleLiveTests {
    private static final Path LIVE = Path.of("src/test/resources/live/observe13-water-oscillation.json");
    private static final UUID SPIRIT = UUID.fromString("f58b83b6-c9a0-406d-967a-822843debf54");

    @Test
    void allow_LiveReplanCadence_KeepsOneRoutePerWindow() throws IOException {
        JsonNode live = new ObjectMapper().readTree(Files.readString(LIVE, StandardCharsets.UTF_8));
        RouteThrottle throttle = new RouteThrottle();
        int allowed = 0;
        for (JsonNode node : live.get("route_attempt_ticks")) {
            if (throttle.allow(SPIRIT, node.asLong())) {
                allowed++;
            }
        }

        assertTrue(
                allowed <= 3,
                "spirit-1 строил маршрут каждые 3–7 с (" + live.get("route_attempt_ticks") + "), сервер получал "
                        + "Can't keep up: за окно " + RouteThrottle.MIN_TICKS + " тиков допустим один расчёт, а "
                        + "разрешено " + allowed);
        assertTrue(allowed >= 2, "Ограничение не должно запрещать все расчёты: разрешено " + allowed);
    }

    @Test
    void logGate_RepeatedSuppressedCalls_OneLinePerWindow() {
        long window = 1200;
        RouteThrottle logs = new RouteThrottle(window);
        int lines = 0;
        for (long tick = 100; tick < 100 + window; tick += 2) {
            if (logs.allow(SPIRIT, tick)) {
                lines++;
            }
        }
        assertTrue(
                lines == 1,
                "RouteDetour писал INFO на каждый подавленный вызов (657 строк за 17 мин): за окно " + window
                        + " тиков допустима одна строка, а их " + lines);
        assertTrue(
                logs.allow(SPIRIT, 100 + window), "Следующее окно снова даёт одну строку: лог не замолкает навсегда");
    }

    @Test
    void allow_DifferentSpirits_AreIndependent() throws IOException {
        JsonNode live = new ObjectMapper().readTree(Files.readString(LIVE, StandardCharsets.UTF_8));
        RouteThrottle throttle = new RouteThrottle();
        UUID other = UUID.fromString("c48663b4-da59-43d9-9804-81a680580300");
        long now = live.get("route_attempt_ticks").get(1).asLong();

        assertTrue(throttle.allow(SPIRIT, 0), "Первый расчёт духа разрешён");
        assertFalse(
                throttle.allow(SPIRIT, now),
                "Повторный расчёт того же духа внутри окна " + RouteThrottle.MIN_TICKS + " тиков запрещён");
        assertTrue(throttle.allow(other, now), "Ограничение одного духа не блокирует расчёт другому");
    }
}
