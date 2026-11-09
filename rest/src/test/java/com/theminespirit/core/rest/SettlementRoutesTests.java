package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.board.StageRollback;
import com.theminespirit.core.board.StageView;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SettlementRoutesTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FakeSettlementGateway gateway;
    private Router router;

    @BeforeEach
    void setUp() {
        gateway = new FakeSettlementGateway();
        router = Router.of(new SettlementRoutes(gateway, new IdempotencyCache()).routes());
    }

    private RestResponse send(String method, String path, String body) {
        return router.handle(new RestRequest(method, path, Map.of(), Map.of(), body.getBytes(StandardCharsets.UTF_8)));
    }

    private static String body(String requestId, int stage, String extra) {
        return "{\"request_id\":\"" + requestId + "\",\"stage\":" + stage + extra + "}";
    }

    @Test
    void get_Stage_ReturnsCurrentStageAndRequirements() throws Exception {
        gateway.with(new StageView(
                2,
                4,
                Map.of("BUILD_MINE", 2),
                List.of(new StageView.Requirement(
                        1, "BUILD_MINE", 1, true, StageRollback.Readiness.NOT_READY, false))));

        RestResponse response = send("GET", "/v1/settlement/stage", "");

        assertEquals(200, response.status(), "Чтение этапа обязано отвечать 200: " + response.body());
        JsonNode parsed = MAPPER.readTree(response.body());
        assertEquals(2, parsed.get("stage").asInt(), "GET обязан отдавать текущий этап поселения: " + response.body());
        assertEquals(
                "not_ready",
                parsed.get("requirements").get(0).get("world").asText(),
                "GET обязан отдавать состояние постройки по миру словами ready/not_ready/unknown: " + response.body());
        assertEquals(
                false,
                parsed.get("requirements").get(0).get("optional").asBoolean(),
                "GET обязан отличать обязательную постройку от желательной: " + response.body());
    }

    @Test
    void get_Stage_ClearingCarriesDefectCounts() throws Exception {
        gateway.with(new StageView(
                1,
                4,
                Map.of("CLEAR_GROUNDS", 1),
                List.of(new StageView.Requirement(
                        1,
                        "CLEAR_GROUNDS",
                        1,
                        true,
                        StageRollback.Readiness.NOT_READY,
                        false,
                        Map.of("holes", 3, "mounds", 2, "pillars", 1)))));

        RestResponse response = send("GET", "/v1/settlement/stage", "");

        JsonNode defects =
                MAPPER.readTree(response.body()).get("requirements").get(0).get("defects");
        assertEquals(3, defects.get("holes").asInt(), "REST отдаёт число ям: " + response.body());
        assertEquals(2, defects.get("mounds").asInt(), "REST отдаёт число бугров: " + response.body());
        assertEquals(1, defects.get("pillars").asInt(), "REST отдаёт число столбов: " + response.body());
    }

    @Test
    void post_Rollback_ReturnsChangeAndDefaultsActor() throws Exception {
        String requestId = UUID.randomUUID().toString();

        RestResponse response = send("POST", "/v1/settlement/stage", body(requestId, 1, ""));

        assertEquals(200, response.status(), "Откат этапа обязан отвечать 200: " + response.body());
        JsonNode parsed = MAPPER.readTree(response.body());
        assertEquals(1, parsed.get("stage").asInt(), "Откат обязан отдать целевой этап: " + response.body());
        assertEquals(2, parsed.get("previous").asInt(), "Откат обязан отдать прежний этап: " + response.body());
        assertEquals(requestId, parsed.get("request_id").asText(), "Откат обязан эхать request_id клиента");
        assertEquals(1, gateway.changes().size(), "Откат обязан дойти до платформы ровно один раз");
        assertEquals(
                SettlementChange.DEFAULT_BY,
                gateway.changes().get(0).by(),
                "Без поля 'by' откат подписывается rest: карточка всё равно называет, кем сделан откат");
        assertEquals(
                SettlementChange.DEFAULT_REASON,
                gateway.changes().get(0).reason(),
                "Без поля 'reason' откат называется «" + SettlementChange.DEFAULT_REASON + "»");
    }

    @Test
    void put_Rollback_TreatedAsPost() {
        String requestId = UUID.randomUUID().toString();

        RestResponse response = send("PUT", "/v1/settlement/stage", body(requestId, 1, ",\"by\":\"owner\""));

        assertEquals(200, response.status(), "PUT отката обязан работать так же, как POST: " + response.body());
        assertEquals("owner", gateway.changes().get(0).by(), "PUT обязан передать 'by' в карточку отката");
    }

    @Test
    void post_ReplaySameRequestId_ReturnsSameResponseWithoutSecondRollback() {
        String requestId = UUID.randomUUID().toString();
        String payload = body(requestId, 1, "");
        RestResponse first = send("POST", "/v1/settlement/stage", payload);

        RestResponse replay = send("POST", "/v1/settlement/stage", payload);

        assertEquals(200, replay.status(), "Повтор отката обязан оставаться 200: " + replay.body());
        assertEquals(first.body(), replay.body(), "Идемпотентный повтор обязан вернуть исходный ответ байт в байт");
        assertEquals(1, gateway.changes().size(), "Повтор не должен откатывать этап второй раз");
    }

    @Test
    void post_SameRequestIdDifferentBody_Conflicts() {
        String requestId = UUID.randomUUID().toString();
        send("POST", "/v1/settlement/stage", body(requestId, 1, ""));

        RestResponse response = send("POST", "/v1/settlement/stage", body(requestId, 2, ""));

        assertEquals(
                409,
                response.status(),
                "Тот же request_id с другим этапом обязан отвечать 409: иначе клиент решит, что откат другой. "
                        + response.body());
    }

    @Test
    void post_UnknownField_Returns400() {
        String requestId = UUID.randomUUID().toString();

        RestResponse response = send("POST", "/v1/settlement/stage", body(requestId, 1, ",\"force\":true"));

        assertEquals(400, response.status(), "Лишнее поле обязано отвергаться: " + response.body());
        assertTrue(
                response.body().contains("force"),
                "Ошибка обязана назвать лишнее поле: иначе клиент не поймёт, что убрать. Ответ: " + response.body());
        assertEquals(0, gateway.changes().size(), "Отвергнутый запрос не имеет права менять доску");
    }

    @Test
    void post_MissingRequestId_Returns400() {
        RestResponse response = send("POST", "/v1/settlement/stage", "{\"stage\":1}");

        assertEquals(
                400,
                response.status(),
                "Откат без request_id обязан отвергаться: без UUID клиента повтор отката не отличить от нового. "
                        + response.body());
        assertEquals(0, gateway.changes().size(), "Отвергнутый запрос не имеет права менять доску");
    }

    @Test
    void post_StageBelowOne_Returns400() {
        String requestId = UUID.randomUUID().toString();

        RestResponse response = send("POST", "/v1/settlement/stage", body(requestId, 0, ""));

        assertEquals(400, response.status(), "Нулевой этап обязан отвергаться: " + response.body());
    }
}
