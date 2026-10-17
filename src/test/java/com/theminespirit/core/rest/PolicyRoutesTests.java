package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockBox;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.UnprotectArea;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PolicyRoutesTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FakePolicyGateway gateway;
    private Router router;

    @BeforeEach
    void setUp() {
        gateway = new FakePolicyGateway();
        router = Router.of(new PolicyRoutes(gateway, new IdempotencyCache()).routes());
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private RestResponse send(String method, String path, String body) {
        return router.handle(new RestRequest(method, path, Map.of(), Map.of(), utf8(body)));
    }

    private static String body(String requestId, String extra) {
        return "{\"request_id\":\"" + requestId + "\",\"dimension\":\"minecraft:overworld\""
                + ",\"min\":[10,64,10],\"max\":[11,65,11],\"granted_by\":\"игрок\""
                + ",\"reason\":\"убери постройку\"" + extra + "}";
    }

    @Test
    void post_NewArea_Returns201WithIdAndRequestId() throws Exception {
        String requestId = UUID.randomUUID().toString();

        RestResponse response = send("POST", "/v1/policy/unprotect", body(requestId, ""));

        assertEquals(201, response.status(), "Выдача разрешения обязана отвечать 201: " + response.body());
        JsonNode parsed = MAPPER.readTree(response.body());
        assertEquals("unprotect-" + requestId, parsed.get("id").asText(),
                "id разрешения мод назначает от request_id: " + response.body());
        assertEquals(requestId, parsed.get("request_id").asText(), "201 обязан эхать request_id клиента: " + response.body());
        assertEquals(3, parsed.get("min").size(), "201 обязан отдавать угол min массивом: " + response.body());
        assertEquals(1, gateway.grants().size(), "Выдача обязана создать одно разрешение");
    }

    @Test
    void post_ReplaySameRequestId_ReturnsSameAreaWithoutDuplicate() {
        String requestId = UUID.randomUUID().toString();
        String payload = body(requestId, ",\"ttl_seconds\":3600");
        RestResponse first = send("POST", "/v1/policy/unprotect", payload);

        RestResponse replay = send("POST", "/v1/policy/unprotect", payload);

        assertEquals(201, replay.status(), "Повтор того же запроса обязан оставаться 201: " + replay.body());
        assertEquals(first.body(), replay.body(), "Идемпотентный повтор обязан вернуть исходный ответ байт в байт");
        assertEquals(1, gateway.grants().size(), "Повтор не должен выдавать второе разрешение");
    }

    @Test
    void post_SameRequestIdDifferentBody_Conflicts() {
        String requestId = UUID.randomUUID().toString();
        send("POST", "/v1/policy/unprotect", body(requestId, ""));

        RestResponse response = send("POST", "/v1/policy/unprotect", body(requestId, ",\"ttl_seconds\":3600"));

        assertEquals(409, response.status(), "Тот же request_id с другим телом обязан давать 409: " + response.body());
        assertEquals(1, gateway.grants().size(), "Конфликт не должен выдавать второе разрешение");
    }

    @Test
    void post_GatewayRefusesOverVolume_Returns400NamingLimit() {
        gateway.refuseWith("Объём области 9000 больше предела 4096 (protection.json → maxUnprotectVolume)");

        RestResponse response = send("POST", "/v1/policy/unprotect", body(UUID.randomUUID().toString(), ""));

        assertEquals(400, response.status(), "Отказ хранилища обязан отдаваться 400, а не 500: " + response.body());
        assertTrue(response.body().contains("4096"), "400 обязан называть предел из данных: " + response.body());
    }

    @Test
    void post_MissingReason_Returns400() {
        RestResponse response = send("POST", "/v1/policy/unprotect",
                "{\"request_id\":\"" + UUID.randomUUID() + "\",\"dimension\":\"minecraft:overworld\","
                        + "\"min\":[10,64,10],\"max\":[11,65,11],\"granted_by\":\"игрок\"}");

        assertEquals(400, response.status(), "Без причины разрешение не выдаётся: " + response.body());
        assertTrue(response.body().contains("reason"), "400 обязан называть поле reason: " + response.body());
        assertEquals(0, gateway.grants().size(), "Отклонённое тело не должно доходить до хранилища");
    }

    @Test
    void get_Areas_ReturnsList() throws Exception {
        gateway.with(new UnprotectArea("unprotect-a", "minecraft:overworld",
                new BlockBox(new BlockCoord(0, 0, 0), new BlockCoord(2, 2, 2)), "игрок", 7, "убери постройку", 0));

        RestResponse response = send("GET", "/v1/policy/unprotect", "");

        assertEquals(200, response.status(), "Список разрешений обязан отвечать 200: " + response.body());
        JsonNode areas = MAPPER.readTree(response.body()).get("areas");
        assertEquals(1, areas.size(), "GET обязан вернуть все разрешения: " + response.body());
        assertEquals("unprotect-a", areas.get(0).get("id").asText(), "GET обязан отдавать id: " + response.body());
    }

    @Test
    void delete_KnownArea_RemovesAndReturnsId() throws Exception {
        gateway.with(new UnprotectArea("unprotect-a", "minecraft:overworld",
                new BlockBox(new BlockCoord(0, 0, 0), new BlockCoord(2, 2, 2)), "игрок", 7, "убери постройку", 0));

        RestResponse response = send("DELETE", "/v1/policy/unprotect/unprotect-a", "");

        assertEquals(200, response.status(), "Снятие разрешения обязано отвечать 200: " + response.body());
        assertTrue(MAPPER.readTree(response.body()).get("removed").asBoolean(),
                "Ответ снятия обязан нести removed: true: " + response.body());
        assertEquals(0, gateway.areas().size(), "Снятое разрешение обязано исчезнуть из хранилища");
    }

    @Test
    void delete_UnknownArea_Returns404() {
        RestResponse response = send("DELETE", "/v1/policy/unprotect/missing", "");

        assertEquals(404, response.status(), "Снятие несуществующего разрешения обязано отвечать 404: " + response.body());
        assertTrue(response.body().contains("не найдено"), "404 обязан объяснять причину: " + response.body());
    }
}
