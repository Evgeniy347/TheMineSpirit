package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
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

    private static String mapBody(String requestId, int revision) {
        return "{\"request_id\":\"" + requestId + "\",\"revision\":" + revision
                + ",\"anchor\":{\"x\":0,\"z\":0},\"plots\":[],\"streets\":[],\"wall\":[]}";
    }

    @Test
    void get_Plan_ReturnsGatewayMap() {
        gateway.withPlan("{\"revision\":3,\"plots\":[]}");

        RestResponse response = send("GET", "/v1/town/plan", "");

        assertEquals(
                200,
                response.status(),
                "Карта поселения обязана отдаваться с кодом 200: " + response.body()
                        + " Иначе тест get_Plan_ReturnsGatewayMap перестанет ловить эту поломку. При смене get_Plan_ReturnsGatewayMap проверь это утверждение.");
        assertEquals(
                "{\"revision\":3,\"plots\":[]}",
                response.body(),
                "GET /v1/town/plan обязан отдавать карту платформы без переупаковки: " + response.body()
                        + " Иначе тест get_Plan_ReturnsGatewayMap перестанет ловить эту поломку. При смене get_Plan_ReturnsGatewayMap проверь это утверждение.");
    }

    @Test
    void get_Terrain_ServesPngForRequestedBox() {
        gateway.withImage(new byte[] {9, 8, 7});

        RestResponse response = router.handle(new RestRequest(
                "GET",
                "/v1/town/terrain.png",
                Map.of("x1", "-10", "z1", "20", "x2", "30", "z2", "60"),
                Map.of(),
                new byte[0]));

        assertEquals(
                "image/png",
                response.binary().size() > 0 ? "image/png" : "application/json",
                "Карта местности отдаётся картинкой, а не JSON: " + response.body()
                        + " Иначе тест get_Terrain_ServesPngForRequestedBox перестанет ловить эту поломку. При смене get_Terrain_ServesPngForRequestedBox проверь это утверждение.");
        assertArrayEquals(
                new byte[] {9, 8, 7},
                response.binary().bytes(),
                "PNG местности идёт от платформы без пересборки"
                        + " Иначе тест get_Terrain_ServesPngForRequestedBox перестанет ловить эту поломку. При смене get_Terrain_ServesPngForRequestedBox проверь это утверждение.");
    }

    @Test
    void get_TerrainWithoutX2_RejectedAsBadRequest() {
        RestResponse failure = router.handle(
                new RestRequest("GET", "/v1/town/terrain.png", Map.of("x1", "-10", "z1", "20"), Map.of(), new byte[0]));

        assertEquals(
                400,
                failure.status(),
                "Рамка местности без координаты — структурная ошибка запроса: " + failure.body()
                        + " Иначе тест get_TerrainWithoutX2_RejectedAsBadRequest перестанет ловить эту поломку. При смене get_TerrainWithoutX2_RejectedAsBadRequest проверь это утверждение.");
        assertTrue(
                failure.body().contains("x2"),
                "Ошибка обязана называть пропущенную координату x2: " + failure.body()
                        + " Иначе тест get_TerrainWithoutX2_RejectedAsBadRequest перестанет ловить эту поломку. При смене get_TerrainWithoutX2_RejectedAsBadRequest проверь это утверждение.");
    }

    @Test
    void drawio_DefaultFrame_ServesXml() {
        gateway.withDrawio("<mxfile/>");

        RestResponse response = send("GET", "/v1/town/plan.drawio", "");

        assertEquals(
                200,
                response.status(),
                "Карта .drawio обязана отдаваться с кодом 200: " + response.body()
                        + " Иначе тест drawio_DefaultFrame_ServesXml перестанет ловить эту поломку. При смене drawio_DefaultFrame_ServesXml проверь это утверждение.");
        assertEquals(
                "application/xml; charset=utf-8",
                response.headers().get("Content-Type"),
                "Карта .drawio отдаётся XML, а не JSON: иначе diagrams.net не откроет файл; получено "
                        + response.headers()
                        + " Иначе тест drawio_DefaultFrame_ServesXml перестанет ловить эту поломку. При смене drawio_DefaultFrame_ServesXml проверь это утверждение.");
        assertEquals(
                "<mxfile/>",
                response.body(),
                "XML карты идёт от платформы без переупаковки: " + response.body()
                        + " Иначе тест drawio_DefaultFrame_ServesXml перестанет ловить эту поломку. При смене drawio_DefaultFrame_ServesXml проверь это утверждение.");
        assertEquals(
                List.of("default"),
                gateway.drawioFrames(),
                "Без рамки .drawio берёт кадр плана целиком: " + gateway.drawioFrames()
                        + " Иначе тест drawio_DefaultFrame_ServesXml перестанет ловить эту поломку. При смене drawio_DefaultFrame_ServesXml проверь это утверждение.");
    }

    @Test
    void drawio_RequestedFrame_PassesCoordinates() {
        RestResponse response = router.handle(new RestRequest(
                "GET",
                "/v1/town/plan.drawio",
                Map.of("x1", "-20", "z1", "-10", "x2", "20", "z2", "50", "scale", "3"),
                Map.of(),
                new byte[0]));

        assertEquals(
                200,
                response.status(),
                "Рамка .drawio с координатами обязана приниматься: " + response.body()
                        + " Иначе тест drawio_RequestedFrame_PassesCoordinates перестанет ловить эту поломку. При смене drawio_RequestedFrame_PassesCoordinates проверь это утверждение.");
        assertEquals(
                List.of("-20,-10,20,50,3"),
                gateway.drawioFrames(),
                "Координаты рамки и масштаб обязаны дойти до платформы без подмены: " + gateway.drawioFrames()
                        + " Иначе тест drawio_RequestedFrame_PassesCoordinates перестанет ловить эту поломку. При смене drawio_RequestedFrame_PassesCoordinates проверь это утверждение.");
    }

    @Test
    void post_Preview_ForwardsBodyAndReturnsPng() {
        RestResponse response = send("POST", "/v1/town/preview", mapBody("preview-1", 3));

        assertEquals(
                200,
                response.status(),
                "Превью плана обязано отвечать 200: " + response.body()
                        + " Иначе тест post_Preview_ForwardsBodyAndReturnsPng перестанет ловить эту поломку. При смене post_Preview_ForwardsBodyAndReturnsPng проверь это утверждение.");
        assertEquals(
                1,
                gateway.previews().size(),
                "Тело превью обязано дойти до платформы ровно один раз: " + gateway.previews()
                        + " Иначе тест post_Preview_ForwardsBodyAndReturnsPng перестанет ловить эту поломку. При смене post_Preview_ForwardsBodyAndReturnsPng проверь это утверждение.");
        assertArrayEquals(
                new byte[] {1, 2, 3},
                response.binary().bytes(),
                "Превью возвращается картинкой платформы"
                        + " Иначе тест post_Preview_ForwardsBodyAndReturnsPng перестанет ловить эту поломку. При смене post_Preview_ForwardsBodyAndReturnsPng проверь это утверждение.");
    }

    @Test
    void post_Apply_ForwardsBodyAndReturnsAnswer() {
        gateway.withAnswer("{\"ok\":true,\"revision\":4}");

        RestResponse response = send("POST", "/v1/town/apply", mapBody("apply-1", 3));

        assertEquals(
                200,
                response.status(),
                "Принятая карта обязана отвечать 200: " + response.body()
                        + " Иначе тест post_Apply_ForwardsBodyAndReturnsAnswer перестанет ловить эту поломку. При смене post_Apply_ForwardsBodyAndReturnsAnswer проверь это утверждение.");
        assertEquals(
                "{\"ok\":true,\"revision\":4}",
                response.body(),
                "Ответ применения карты идёт от платформы: " + response.body()
                        + " Иначе тест post_Apply_ForwardsBodyAndReturnsAnswer перестанет ловить эту поломку. При смене post_Apply_ForwardsBodyAndReturnsAnswer проверь это утверждение.");
        assertEquals(
                1,
                gateway.applied().size(),
                "Карта обязана дойти до платформы ровно один раз: " + gateway.applied()
                        + " Иначе тест post_Apply_ForwardsBodyAndReturnsAnswer перестанет ловить эту поломку. При смене post_Apply_ForwardsBodyAndReturnsAnswer проверь это утверждение.");
    }

    @Test
    void apply_RepeatedRequestId_ReplayWithoutSecondCall() {
        String payload = mapBody("apply-replay", 3);
        RestResponse first = send("POST", "/v1/town/apply", payload);

        RestResponse replay = send("POST", "/v1/town/apply", payload);

        assertEquals(
                first.body(),
                replay.body(),
                "Повтор request_id обязан вернуть прежний ответ: " + replay.body()
                        + " Иначе тест apply_RepeatedRequestId_ReplayWithoutSecondCall перестанет ловить эту поломку. При смене apply_RepeatedRequestId_ReplayWithoutSecondCall проверь это утверждение.");
        assertEquals(
                1,
                gateway.applied().size(),
                "Повтор request_id не должен применяться к миру второй раз: " + gateway.applied()
                        + " Иначе тест apply_RepeatedRequestId_ReplayWithoutSecondCall перестанет ловить эту поломку. При смене apply_RepeatedRequestId_ReplayWithoutSecondCall проверь это утверждение.");
    }

    @Test
    void apply_SameRequestIdDifferentBody_ConflictedResponse() {
        send("POST", "/v1/town/apply", mapBody("apply-conflict", 3));

        RestResponse conflict = send("POST", "/v1/town/apply", mapBody("apply-conflict", 4));

        assertEquals(
                409,
                conflict.status(),
                "Тот же request_id с другим телом обязан давать 409: " + conflict.body()
                        + " Иначе тест apply_SameRequestIdDifferentBody_ConflictedResponse перестанет ловить эту поломку. При смене apply_SameRequestIdDifferentBody_ConflictedResponse проверь это утверждение.");
        assertTrue(
                conflict.body().contains("conflict"),
                "Ответ 409 обязан называть код conflict: " + conflict.body()
                        + " Иначе тест apply_SameRequestIdDifferentBody_ConflictedResponse перестанет ловить эту поломку. При смене apply_SameRequestIdDifferentBody_ConflictedResponse проверь это утверждение.");
    }

    @Test
    void apply_MissingRequestId_RejectedAsBadRequest() {
        RestResponse failure = send("POST", "/v1/town/apply", "{\"plots\":[]}");

        assertEquals(
                400,
                failure.status(),
                "Карта без request_id — структурная ошибка тела, а не плана: " + failure.body()
                        + " Иначе тест apply_MissingRequestId_RejectedAsBadRequest перестанет ловить эту поломку. При смене apply_MissingRequestId_RejectedAsBadRequest проверь это утверждение.");
        assertTrue(
                failure.body().contains("request_id"),
                "Ошибка обязана называть пропущенное поле request_id: " + failure.body()
                        + " Иначе тест apply_MissingRequestId_RejectedAsBadRequest перестанет ловить эту поломку. При смене apply_MissingRequestId_RejectedAsBadRequest проверь это утверждение.");
    }

    @Test
    void apply_FailedValidation_AllowsRetry() {
        gateway.withFailure(new ApiException(
                ErrorCode.INVALID_PLAN, "участки 'barn' и 'storage' накладываются: разведите их по клеткам"));
        String payload = mapBody("apply-retry", 3);
        RestResponse failed = send("POST", "/v1/town/apply", payload);

        gateway.withFailure(null);
        RestResponse retry = send("POST", "/v1/town/apply", payload);

        assertEquals(
                422,
                failed.status(),
                "Наложение участков обязано давать 422 invalid_plan: " + failed.body()
                        + " Иначе тест apply_FailedValidation_AllowsRetry перестанет ловить эту поломку. При смене apply_FailedValidation_AllowsRetry проверь это утверждение.");
        assertEquals(
                200,
                retry.status(),
                "После отказа плана тот же request_id обязан приниматься заново: " + retry.body()
                        + " Иначе тест apply_FailedValidation_AllowsRetry перестанет ловить эту поломку. При смене apply_FailedValidation_AllowsRetry проверь это утверждение.");
    }
}
