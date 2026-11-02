package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HealthRoutesTests {
    private static final long NOW = 1_800_000_300_000L;

    private static RestResponse get(HealthGateway gateway, String path) {
        Router router = Router.of(HealthRoutes.of(gateway));
        return router.handle(new RestRequest("GET", path, Map.of(), Map.of(), new byte[0]));
    }

    @Test
    void health_StaleTick_ReportsHungJson() {
        HealthSnapshot snapshot = new HealthSnapshot(NOW, NOW - 300_000, 42, 180_000, false);

        String json = HealthRoutes.json(snapshot);

        assertEquals(
                "{\"verdict\":\"hung\",\"age_ms\":300000,\"tick\":42,\"last_tick_ms\":" + (NOW - 300_000)
                        + ",\"threshold_ms\":180000,\"debug_paused\":false}",
                json,
                "GET /v1/health — контракт сторожа зависания: он берёт verdict и age_ms, чтобы отличить живой тик "
                        + "от зависания без тяжёлого /v1/bots; смена ключей сломает снятие дампа и перезапуск");
    }

    @Test
    void health_LiveGateway_ReportsHungAgeFromRequestTime() {
        HealthGateway gateway = nowMillis -> new HealthSnapshot(nowMillis, nowMillis - 300_000, 42, 180_000, false);

        RestResponse response = get(gateway, HealthRoutes.PATH);

        assertTrue(
                response.body().contains("\"verdict\":\"hung\"")
                        && response.body().contains("\"age_ms\":300000"),
                "Маршрут обязан считать возраст от времени запроса, а не от зашитого в снимке значения: "
                        + "иначе после долгого аптайма health отдаёт неверный возраст. Тело: " + response.body());
    }

    @Test
    void health_NoTickYet_ReportsUnknownWithMinusOneAge() {
        HealthGateway gateway = nowMillis -> new HealthSnapshot(nowMillis, 0, 0, 180_000, false);

        RestResponse response = get(gateway, HealthRoutes.PATH);

        assertTrue(
                response.body().contains("\"verdict\":\"unknown\"")
                        && response.body().contains("\"age_ms\":-1"),
                "До первого тика health обязан отвечать unknown с age_ms -1: иначе сторож примет старт сервера "
                        + "за зависание и перезапустит его по кругу. Тело: " + response.body());
    }

    @Test
    void health_DebugLeaseHeld_ReportsDebug() {
        HealthGateway gateway = nowMillis -> new HealthSnapshot(nowMillis, 0, 0, 180_000, true);

        RestResponse response = get(gateway, HealthRoutes.PATH);

        assertEquals(
                "{\"verdict\":\"debug\",\"age_ms\":-1,\"tick\":0,\"last_tick_ms\":0,\"threshold_ms\":180000,"
                        + "\"debug_paused\":true}",
                response.body(),
                "При активном замке живой отладки health обязан отвечать debug: сторож не трогает сервер, "
                        + "остановленный на брейкпоинте, даже если тик стоит");
    }

    @Test
    void health_UnknownPath_ReturnsNotFound() {
        RestResponse response = get(HealthGateway.unknown(), "/v1/health/extra");

        assertEquals(
                404,
                response.status(),
                "Лишний сегмент после /v1/health — неизвестный путь: иначе клиент получит health вместо ошибки "
                        + "и не заметит опечатку в адресе");
    }

    @Test
    void routes_HealthRegistered_OnlyGetAllowed() {
        List<Route> routes = HealthRoutes.of(HealthGateway.unknown());

        assertEquals(
                List.of("GET " + HealthRoutes.PATH),
                routes.stream()
                        .map(route -> route.method() + " " + route.pattern())
                        .toList(),
                "Health — только чтение: POST/DELETE на /v1/health обязаны получать 405, а не побочный эффект");
    }
}
