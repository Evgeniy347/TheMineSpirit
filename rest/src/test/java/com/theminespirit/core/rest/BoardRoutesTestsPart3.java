package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BoardRoutesTestsPart3 {
    private FakeBoardGateway gateway;
    private IdempotencyCache idempotency;
    private Router router;

    @BeforeEach
    void setUp() {
        gateway = new FakeBoardGateway();
        idempotency = new IdempotencyCache();
        router = Router.of(new BoardRoutes(gateway, idempotency).routes());
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private RestResponse send(String method, String path, String body) {
        return router.handle(new RestRequest(method, path, Map.of(), Map.of(), utf8(body)));
    }

    @Test
    void delete_UnknownTask_ReturnsNotFound() {
        RestResponse response = send("DELETE", "/v1/board/tasks/board-404", "");

        assertEquals(
                404,
                response.status(),
                "Отмена неизвестного задания обязана давать 404: " + response.body()
                        + " Иначе тест delete_UnknownTask_ReturnsNotFound перестанет ловить эту поломку. При смене delete_UnknownTask_ReturnsNotFound проверь это утверждение.");
        assertFalse(
                gateway.cancels().contains("board-404"),
                "404 не должен трогать доску отменой"
                        + " Иначе тест delete_UnknownTask_ReturnsNotFound перестанет ловить эту поломку. При смене delete_UnknownTask_ReturnsNotFound проверь это утверждение.");
    }

    @Test
    void routes_AllEndpoints_AreRegistered() {
        Set<String> keys = new HashSet<>();
        new BoardRoutes(gateway, idempotency)
                .routes()
                .forEach(route -> keys.add(route.method() + " " + route.pattern()));

        assertEquals(
                Set.of(
                        "GET /v1/board",
                        "GET /v1/board/log",
                        "POST /v1/board/tasks",
                        "POST /v1/board/tasks/{id}/unblock",
                        "DELETE /v1/board/tasks/{id}"),
                keys,
                "Доска обязана давать наблюдение, постановку, снятие блокировки и отмену: иначе оператор "
                        + "не сможет вернуть задание после исправления причины. При смене "
                        + "routes_AllEndpoints_AreRegistered проверь BoardRoutes.routes.");
    }
}
