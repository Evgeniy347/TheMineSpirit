package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.board.WarehouseOrder;
import com.theminespirit.core.observation.BlockCoord;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WarehouseRoutesTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FakeWarehouseGateway gateway;
    private Router router;

    @BeforeEach
    void setUp() {
        gateway = new FakeWarehouseGateway();
        router = Router.of(new WarehouseRoutes(gateway, new IdempotencyCache()).routes());
    }

    @Test
    void get_Warehouse_ReturnsRequiredLevelAndMaterials() throws Exception {
        RestResponse response = send("GET", "/v1/settlement/warehouse", "");

        assertEquals(
                200,
                response.status(),
                "Чтение решения о складе обязано отвечать 200: " + response.body()
                        + " Иначе тест get_Warehouse_ReturnsRequiredLevelAndMaterials перестанет ловить эту поломку. При смене get_Warehouse_ReturnsRequiredLevelAndMaterials проверь это утверждение.");
        JsonNode parsed = MAPPER.readTree(response.body());
        assertEquals(
                2,
                parsed.get("required_level").asInt(),
                "Этап 2 требует склад второго уровня"
                        + " Иначе тест get_Warehouse_ReturnsRequiredLevelAndMaterials перестанет ловить эту поломку. При смене get_Warehouse_ReturnsRequiredLevelAndMaterials проверь это утверждение.");
        assertTrue(
                parsed.get("order").isNull(),
                "Решения пока нет — order=null: " + response.body()
                        + " Иначе тест get_Warehouse_ReturnsRequiredLevelAndMaterials перестанет ловить эту поломку. При смене get_Warehouse_ReturnsRequiredLevelAndMaterials проверь это утверждение.");
        assertTrue(
                parsed.get("materials").has("oak_planks"),
                "GET называет материалы склада: " + response.body()
                        + " Иначе тест get_Warehouse_ReturnsRequiredLevelAndMaterials перестанет ловить эту поломку. При смене get_Warehouse_ReturnsRequiredLevelAndMaterials проверь это утверждение.");
        assertTrue(
                parsed.has("plan_site"),
                "GET подсказывает место storage из плана поселения: без него стратег гадает координаты"
                        + " Иначе тест get_Warehouse_ReturnsRequiredLevelAndMaterials перестанет ловить эту поломку. При смене get_Warehouse_ReturnsRequiredLevelAndMaterials проверь это утверждение.");
        assertEquals(
                "4,64,5",
                parsed.get("plan_site").asText(),
                "plan_site — место storage из плана"
                        + " Иначе тест get_Warehouse_ReturnsRequiredLevelAndMaterials перестанет ловить эту поломку. При смене get_Warehouse_ReturnsRequiredLevelAndMaterials проверь это утверждение.");
    }

    @Test
    void get_NoPlan_WritesNullPlanSite() throws Exception {
        gateway.with(new WarehouseView(2, 2, 0, true, null, null, Map.of("oak_planks", 40)));

        JsonNode parsed =
                MAPPER.readTree(send("GET", "/v1/settlement/warehouse", "").body());

        assertTrue(
                parsed.has("plan_site") && parsed.get("plan_site").isNull(),
                "Без плана поселения plan_site=null: стратег не должен получать выдуманные координаты"
                        + " Иначе тест get_NoPlan_WritesNullPlanSite перестанет ловить эту поломку. При смене get_NoPlan_WritesNullPlanSite проверь это утверждение.");
    }

    @Test
    void post_Decision_StoresLevelAndSite() throws Exception {
        String requestId = UUID.randomUUID().toString();

        RestResponse response = send(
                "POST",
                "/v1/settlement/warehouse",
                "{\"request_id\":\"" + requestId + "\",\"level\":2,\"site\":\"10,64,-3\"}");

        assertEquals(
                200,
                response.status(),
                "Решение о складе обязано приниматься: " + response.body()
                        + " Иначе тест post_Decision_StoresLevelAndSite перестанет ловить эту поломку. При смене post_Decision_StoresLevelAndSite проверь это утверждение.");
        JsonNode parsed = MAPPER.readTree(response.body());
        assertEquals(
                requestId,
                parsed.get("request_id").asText(),
                "Ответ обязан эхать request_id"
                        + " Иначе тест post_Decision_StoresLevelAndSite перестанет ловить эту поломку. При смене post_Decision_StoresLevelAndSite проверь это утверждение.");
        assertEquals(
                2,
                parsed.get("order").get("level").asInt(),
                "Уровень решения сохранён: " + response.body()
                        + " Иначе тест post_Decision_StoresLevelAndSite перестанет ловить эту поломку. При смене post_Decision_StoresLevelAndSite проверь это утверждение.");
        assertEquals(
                "10,64,-3",
                parsed.get("order").get("site").asText(),
                "Место решения сохранено"
                        + " Иначе тест post_Decision_StoresLevelAndSite перестанет ловить эту поломку. При смене post_Decision_StoresLevelAndSite проверь это утверждение.");
        assertEquals(
                1,
                gateway.decisions().size(),
                "Решение доходит до платформы ровно один раз"
                        + " Иначе тест post_Decision_StoresLevelAndSite перестанет ловить эту поломку. При смене post_Decision_StoresLevelAndSite проверь это утверждение.");
        assertEquals(
                WarehouseOrder.DEFAULT_BY,
                gateway.decisions().get(0).by(),
                "Без 'by' решение подписано стратегом: карточка всё равно называет автора"
                        + " Иначе тест post_Decision_StoresLevelAndSite перестанет ловить эту поломку. При смене post_Decision_StoresLevelAndSite проверь это утверждение.");
    }

    @Test
    void put_Decision_WorksLikePost() {
        RestResponse response = send(
                "PUT",
                "/v1/settlement/warehouse",
                "{\"request_id\":\"" + UUID.randomUUID() + "\",\"level\":1,\"site\":\"1,64,1\",\"by\":\"owner\"}");

        assertEquals(
                200,
                response.status(),
                "PUT решения обязан работать как POST: " + response.body()
                        + " Иначе тест put_Decision_WorksLikePost перестанет ловить эту поломку. При смене put_Decision_WorksLikePost проверь это утверждение.");
        assertEquals(
                "owner",
                gateway.decisions().get(0).by(),
                "PUT обязан передать 'by'"
                        + " Иначе тест put_Decision_WorksLikePost перестанет ловить эту поломку. При смене put_Decision_WorksLikePost проверь это утверждение.");
    }

    @Test
    void post_ReplaySameRequest_ReturnsSameResponseWithoutSecondDecision() {
        String payload = "{\"request_id\":\"" + UUID.randomUUID() + "\",\"level\":2,\"site\":\"10,64,-3\"}";
        RestResponse first = send("POST", "/v1/settlement/warehouse", payload);

        RestResponse replay = send("POST", "/v1/settlement/warehouse", payload);

        assertEquals(
                200,
                replay.status(),
                "Повтор решения обязан оставаться 200: " + replay.body()
                        + " Иначе тест post_ReplaySameRequest_ReturnsSameResponseWithoutSecondDecision перестанет ловить эту поломку. При смене post_ReplaySameRequest_ReturnsSameResponseWithoutSecondDecision проверь это утверждение.");
        assertEquals(
                first.body(),
                replay.body(),
                "Идемпотентный повтор возвращает исходный ответ байт в байт"
                        + " Иначе тест post_ReplaySameRequest_ReturnsSameResponseWithoutSecondDecision перестанет ловить эту поломку. При смене post_ReplaySameRequest_ReturnsSameResponseWithoutSecondDecision проверь это утверждение.");
        assertEquals(
                1,
                gateway.decisions().size(),
                "Повтор не должен решать склад второй раз"
                        + " Иначе тест post_ReplaySameRequest_ReturnsSameResponseWithoutSecondDecision перестанет ловить эту поломку. При смене post_ReplaySameRequest_ReturnsSameResponseWithoutSecondDecision проверь это утверждение.");
    }

    @Test
    void post_SameRequestDifferentBody_Conflicts() {
        String requestId = UUID.randomUUID().toString();
        send(
                "POST",
                "/v1/settlement/warehouse",
                "{\"request_id\":\"" + requestId + "\",\"level\":2,\"site\":\"10,64,-3\"}");

        RestResponse response = send(
                "POST",
                "/v1/settlement/warehouse",
                "{\"request_id\":\"" + requestId + "\",\"level\":3,\"site\":\"10,64,-3\"}");

        assertEquals(
                409,
                response.status(),
                "Тот же request_id с другим уровнем обязан конфликтовать: " + response.body()
                        + " Иначе тест post_SameRequestDifferentBody_Conflicts перестанет ловить эту поломку. При смене post_SameRequestDifferentBody_Conflicts проверь это утверждение.");
    }

    @Test
    void post_UnknownField_Returns400() {
        RestResponse response = send(
                "POST",
                "/v1/settlement/warehouse",
                "{\"request_id\":\"" + UUID.randomUUID() + "\",\"level\":2,\"site\":\"10,64,-3\",\"force\":true}");

        assertEquals(
                400,
                response.status(),
                "Лишнее поле обязано отвергаться: " + response.body()
                        + " Иначе тест post_UnknownField_Returns400 перестанет ловить эту поломку. При смене post_UnknownField_Returns400 проверь это утверждение.");
        assertTrue(
                response.body().contains("force"),
                "Ошибка называет лишнее поле: " + response.body()
                        + " Иначе тест post_UnknownField_Returns400 перестанет ловить эту поломку. При смене post_UnknownField_Returns400 проверь это утверждение.");
        assertEquals(
                0,
                gateway.decisions().size(),
                "Отвергнутый запрос не меняет решение"
                        + " Иначе тест post_UnknownField_Returns400 перестанет ловить эту поломку. При смене post_UnknownField_Returns400 проверь это утверждение.");
    }

    @Test
    void post_LevelBelowOne_Returns400() {
        RestResponse response = send(
                "POST",
                "/v1/settlement/warehouse",
                "{\"request_id\":\"" + UUID.randomUUID() + "\",\"level\":0,\"site\":\"10,64,-3\"}");

        assertEquals(
                400,
                response.status(),
                "Нулевой уровень склада обязан отвергаться: " + response.body()
                        + " Иначе тест post_LevelBelowOne_Returns400 перестанет ловить эту поломку. При смене post_LevelBelowOne_Returns400 проверь это утверждение.");
    }

    @Test
    void post_BadSite_Returns400() {
        RestResponse response = send(
                "POST",
                "/v1/settlement/warehouse",
                "{\"request_id\":\"" + UUID.randomUUID() + "\",\"level\":2,\"site\":\"верх\"}");

        assertEquals(
                400,
                response.status(),
                "Место не координатами обязано отвергаться: " + response.body()
                        + " Иначе тест post_BadSite_Returns400 перестанет ловить эту поломку. При смене post_BadSite_Returns400 проверь это утверждение.");
    }

    @Test
    void changeJson_NullPrevious_WritesNull() throws Exception {
        WarehouseChange change = new WarehouseChange(
                null, new WarehouseOrder(2, new BlockCoord(1, 64, 1), "ai", "нужен склад", 5), true);

        JsonNode parsed = MAPPER.readTree(
                WarehouseJson.changeJson(change, UUID.randomUUID().toString()));

        assertTrue(
                parsed.get("previous").isNull(),
                "Первого решения не было — previous=null: " + parsed
                        + " Иначе тест changeJson_NullPrevious_WritesNull перестанет ловить эту поломку. При смене changeJson_NullPrevious_WritesNull проверь это утверждение.");
        assertEquals(
                2,
                parsed.get("order").get("level").asInt(),
                "order называет уровень решения"
                        + " Иначе тест changeJson_NullPrevious_WritesNull перестанет ловить эту поломку. При смене changeJson_NullPrevious_WritesNull проверь это утверждение.");
    }

    private RestResponse send(String method, String path, String body) {
        return router.handle(new RestRequest(method, path, Map.of(), Map.of(), body.getBytes(StandardCharsets.UTF_8)));
    }
}
