package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TownRoutesTests {
    private FakeTownGateway gateway;
    private Router router;

    @BeforeEach
    void setUp() {
        gateway = new FakeTownGateway();
        router = Router.of(new TownRoutes(gateway, new IdempotencyCache()).routes());
    }

    private RestResponse send(String method, String path, String body) {
        return router.handle(new RestRequest(method, path, Map.of(), Map.of(), body.getBytes(StandardCharsets.UTF_8)));
    }

    private static String editBody(String requestId, String id) {
        return "{\"request_id\":\"" + requestId + "\",\"ops\":[{\"op\":\"move_plot\",\"id\":\"" + id
                + "\",\"x\":1,\"z\":2}]}";
    }

    @Test
    void get_Town_ReturnsGatewayMap() {
        gateway.withView("{\"stage\":2,\"plots\":[]}");

        RestResponse response = send("GET", "/v1/town", "");

        assertEquals(
                200,
                response.status(),
                "Карта поселения обязана отдаваться с кодом 200: " + response.body()
                        + " Иначе тест get_Town_ReturnsGatewayMap перестанет ловить эту поломку. При смене get_Town_ReturnsGatewayMap проверь это утверждение.");
        assertEquals(
                "{\"stage\":2,\"plots\":[]}",
                response.body(),
                "GET /v1/town обязан отдавать карту платформы без переупаковки: " + response.body()
                        + " Иначе тест get_Town_ReturnsGatewayMap перестанет ловить эту поломку. При смене get_Town_ReturnsGatewayMap проверь это утверждение.");
    }

    @Test
    void post_Edits_ForwardsBodyAndReturnsAnswer() {
        RestResponse response = send("POST", "/v1/town/edits", editBody("edit-1", "barn"));

        assertEquals(
                200,
                response.status(),
                "Принятая правка обязана отвечать 200: " + response.body()
                        + " Иначе тест post_Edits_ForwardsBodyAndReturnsAnswer перестанет ловить эту поломку. При смене post_Edits_ForwardsBodyAndReturnsAnswer проверь это утверждение.");
        assertEquals(
                1,
                gateway.edits().size(),
                "Правка обязана дойти до платформы ровно один раз: " + gateway.edits()
                        + " Иначе тест post_Edits_ForwardsBodyAndReturnsAnswer перестанет ловить эту поломку. При смене post_Edits_ForwardsBodyAndReturnsAnswer проверь это утверждение.");
        assertEquals(
                false,
                gateway.dryRuns().get(0),
                "POST /v1/town/edits применяет правку к миру, а не проверяет её: " + gateway.dryRuns()
                        + " Иначе тест post_Edits_ForwardsBodyAndReturnsAnswer перестанет ловить эту поломку. При смене post_Edits_ForwardsBodyAndReturnsAnswer проверь это утверждение.");
    }

    @Test
    void edits_RepeatedRequestId_ReplayWithoutSecondCall() {
        String payload = editBody("edit-replay", "barn");
        RestResponse first = send("POST", "/v1/town/edits", payload);

        RestResponse replay = send("POST", "/v1/town/edits", payload);

        assertEquals(
                first.body(),
                replay.body(),
                "Повтор request_id обязан вернуть прежний ответ: " + replay.body()
                        + " Иначе тест edits_RepeatedRequestId_ReplayWithoutSecondCall перестанет ловить эту поломку. При смене edits_RepeatedRequestId_ReplayWithoutSecondCall проверь это утверждение.");
        assertEquals(
                1,
                gateway.edits().size(),
                "Повтор request_id не должен применяться к миру второй раз: " + gateway.edits()
                        + " Иначе тест edits_RepeatedRequestId_ReplayWithoutSecondCall перестанет ловить эту поломку. При смене edits_RepeatedRequestId_ReplayWithoutSecondCall проверь это утверждение.");
    }

    @Test
    void edits_SameRequestIdDifferentBody_ConflictedResponse() {
        send("POST", "/v1/town/edits", editBody("edit-conflict", "barn"));

        RestResponse conflict = send("POST", "/v1/town/edits", editBody("edit-conflict", "hut"));

        assertEquals(
                409,
                conflict.status(),
                "Тот же request_id с другим телом обязан давать 409: " + conflict.body()
                        + " Иначе тест edits_SameRequestIdDifferentBody_ConflictedResponse перестанет ловить эту поломку. При смене edits_SameRequestIdDifferentBody_ConflictedResponse проверь это утверждение.");
        assertTrue(
                conflict.body().contains("\"conflict\""),
                "Ответ 409 обязан называть код conflict: " + conflict.body()
                        + " Иначе тест edits_SameRequestIdDifferentBody_ConflictedResponse перестанет ловить эту поломку. При смене edits_SameRequestIdDifferentBody_ConflictedResponse проверь это утверждение.");
    }

    @Test
    void edits_MissingRequestId_RejectedAsBadRequest() {
        RestResponse failure = send("POST", "/v1/town/edits", "{\"ops\":[]}");

        assertEquals(
                400,
                failure.status(),
                "Правка без request_id — структурная ошибка тела, а не плана: " + failure.body()
                        + " Иначе тест edits_MissingRequestId_RejectedAsBadRequest перестанет ловить эту поломку. При смене edits_MissingRequestId_RejectedAsBadRequest проверь это утверждение.");
        assertTrue(
                failure.body().contains("request_id"),
                "Ошибка обязана называть пропущенное поле request_id: " + failure.body()
                        + " Иначе тест edits_MissingRequestId_RejectedAsBadRequest перестанет ловить эту поломку. При смене edits_MissingRequestId_RejectedAsBadRequest проверь это утверждение.");
    }

    @Test
    void edits_FailedApply_AllowsRetry() {
        gateway.withFailure(new ApiException(ErrorCode.INVALID_PLAN, "участок 'barn' накладывается на 'storage'"));
        String payload = editBody("edit-retry", "barn");
        RestResponse failed = send("POST", "/v1/town/edits", payload);

        gateway.withFailure(null);
        RestResponse retry = send("POST", "/v1/town/edits", payload);

        assertEquals(
                422,
                failed.status(),
                "Наложение участков обязано давать 422 invalid_plan: " + failed.body()
                        + " Иначе тест edits_FailedApply_AllowsRetry перестанет ловить эту поломку. При смене edits_FailedApply_AllowsRetry проверь это утверждение.");
        assertEquals(
                200,
                retry.status(),
                "После отказа плана тот же request_id обязан приниматься заново: " + retry.body()
                        + " Иначе тест edits_FailedApply_AllowsRetry перестанет ловить эту поломку. При смене edits_FailedApply_AllowsRetry проверь это утверждение.");
        assertEquals(
                2,
                gateway.edits().size(),
                "Неудачная правка не должна запоминаться как применённая: " + gateway.edits()
                        + " Иначе тест edits_FailedApply_AllowsRetry перестанет ловить эту поломку. При смене edits_FailedApply_AllowsRetry проверь это утверждение.");
    }

    @Test
    void post_Validate_AlwaysDryRun() {
        RestResponse response = send("POST", "/v1/town/validate", editBody("check-1", "barn"));

        assertEquals(
                200,
                response.status(),
                "Сухой прогон обязан отвечать 200 без применения: " + response.body()
                        + " Иначе тест post_Validate_AlwaysDryRun перестанет ловить эту поломку. При смене post_Validate_AlwaysDryRun проверь это утверждение.");
        assertEquals(
                true,
                gateway.dryRuns().get(0),
                "Маршрут validate обязан просить у платформы сухой прогон: "
                        + gateway.dryRuns()
                        + " Иначе тест post_Validate_AlwaysDryRun перестанет ловить эту поломку. При смене post_Validate_AlwaysDryRun проверь это утверждение.");
    }
}
