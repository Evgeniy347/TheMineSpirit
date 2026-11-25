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
    private static final String DRAWIO = "<mxfile><diagram><mxGraphModel/></diagram></mxfile>";
    private FakeTownGateway gateway;
    private Router router;

    @BeforeEach
    void setUp() {
        gateway = new FakeTownGateway();
        router = Router.of(new TownRoutes(gateway, new IdempotencyCache()).routes());
    }

    private RestResponse send(String method, String path, String body) {
        return send(method, path, Map.of(), body);
    }

    private RestResponse send(String method, String path, Map<String, String> query, String body) {
        return router.handle(new RestRequest(method, path, query, Map.of(), body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void get_Plan_ReturnsDrawio() {
        gateway.withDrawio("<mxfile/>");

        RestResponse response = send("GET", "/v1/town/plan", "");

        assertEquals(
                200,
                response.status(),
                "Карта поселения обязана отдаваться с кодом 200: " + response.body()
                        + " Иначе тест get_Plan_ReturnsDrawio перестанет ловить эту поломку. При смене "
                        + "get_Plan_ReturnsDrawio проверь код ответа и тело XML");
        assertEquals(
                "<mxfile/>",
                response.body(),
                "GET /v1/town/plan обязан отдавать .drawio платформы без переупаковки: " + response.body()
                        + " Иначе тест get_Plan_ReturnsDrawio перестанет ловить эту поломку. При смене "
                        + "get_Plan_ReturnsDrawio проверь, что JSON больше не отдаётся");
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

        assertArrayEquals(
                new byte[] {9, 8, 7},
                response.binary().bytes(),
                "PNG местности идёт от платформы без пересборки, иначе подложка .drawio разойдётся с миром; "
                        + "Иначе тест get_Terrain_ServesPngForRequestedBox перестанет ловить эту поломку. При "
                        + "смене get_Terrain_ServesPngForRequestedBox проверь рамку и байты PNG");
    }

    @Test
    void drawio_DefaultFrame_ServesXml() {
        gateway.withDrawio("<mxfile/>");

        RestResponse response = send("GET", "/v1/town/plan.drawio", "");

        assertEquals(
                "application/xml; charset=utf-8",
                response.headers().get("Content-Type"),
                "Карта .drawio отдаётся XML, а не JSON: иначе diagrams.net не откроет файл; получено "
                        + response.headers()
                        + " Иначе тест drawio_DefaultFrame_ServesXml перестанет ловить эту поломку. При смене "
                        + "drawio_DefaultFrame_ServesXml проверь заголовок Content-Type");
        assertEquals(
                List.of("default"),
                gateway.drawioFrames(),
                "Без рамки .drawio берёт кадр плана целиком: " + gateway.drawioFrames()
                        + " Иначе тест drawio_DefaultFrame_ServesXml перестанет ловить эту поломку. При смене "
                        + "drawio_DefaultFrame_ServesXml проверь кадр по умолчанию");
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
                        + " Иначе тест drawio_RequestedFrame_PassesCoordinates перестанет ловить эту поломку. "
                        + "При смене drawio_RequestedFrame_PassesCoordinates проверь код 200");
        assertEquals(
                List.of("-20,-10,20,50,3"),
                gateway.drawioFrames(),
                "Координаты рамки и масштаб обязаны дойти до платформы без подмены: " + gateway.drawioFrames()
                        + " Иначе тест drawio_RequestedFrame_PassesCoordinates перестанет ловить эту поломку. "
                        + "При смене drawio_RequestedFrame_PassesCoordinates проверь порядок координат");
    }

    @Test
    void post_Preview_ForwardsDrawioAndReturnsPng() {
        RestResponse response = send("POST", "/v1/town/preview", DRAWIO);

        assertEquals(
                1,
                gateway.previews().size(),
                "Тело .drawio обязано дойти до платформы ровно один раз: " + gateway.previews()
                        + " Иначе тест post_Preview_ForwardsDrawioAndReturnsPng перестанет ловить эту поломку. "
                        + "При смене post_Preview_ForwardsDrawioAndReturnsPng проверь пересылку тела");
        assertArrayEquals(
                new byte[] {1, 2, 3},
                response.binary().bytes(),
                "Превью возвращается картинкой платформы, а не XML: иначе клиент не увидит план до применения; "
                        + "Иначе тест post_Preview_ForwardsDrawioAndReturnsPng перестанет ловить эту поломку. При "
                        + "смене post_Preview_ForwardsDrawioAndReturnsPng проверь PNG в ответе");
        assertEquals(
                DRAWIO,
                gateway.previews().get(0),
                "Маршрут превью обязан передать .drawio без правок: " + gateway.previews()
                        + " Иначе тест post_Preview_ForwardsDrawioAndReturnsPng перестанет ловить эту поломку. "
                        + "При смене post_Preview_ForwardsDrawioAndReturnsPng проверь исходное тело");
    }

    @Test
    void post_Apply_ForwardsDrawioAndReturnsAnswer() {
        gateway.withAnswer("{\"ok\":true,\"revision\":4}");

        RestResponse response = send("POST", "/v1/town/apply", Map.of("request_id", "apply-1"), DRAWIO);

        assertEquals(
                200,
                response.status(),
                "Принятая карта обязана отвечать 200: " + response.body()
                        + " Иначе тест post_Apply_ForwardsDrawioAndReturnsAnswer перестанет ловить эту поломку. "
                        + "При смене post_Apply_ForwardsDrawioAndReturnsAnswer проверь код ответа");
        assertEquals(
                "{\"ok\":true,\"revision\":4}",
                response.body(),
                "Ответ применения карты идёт от платформы: " + response.body()
                        + " Иначе тест post_Apply_ForwardsDrawioAndReturnsAnswer перестанет ловить эту поломку. "
                        + "При смене post_Apply_ForwardsDrawioAndReturnsAnswer проверь тело ответа");
        assertEquals(
                DRAWIO,
                gateway.applied().get(0),
                "Карта .drawio обязана дойти до платформы без переупаковки: " + gateway.applied()
                        + " Иначе тест post_Apply_ForwardsDrawioAndReturnsAnswer перестанет ловить эту поломку. "
                        + "При смене post_Apply_ForwardsDrawioAndReturnsAnswer проверь исходное тело");
    }

    @Test
    void apply_RepeatedRequestId_ReplayWithoutSecondCall() {
        Map<String, String> query = Map.of("request_id", "apply-replay");
        RestResponse first = send("POST", "/v1/town/apply", query, DRAWIO);

        RestResponse replay = send("POST", "/v1/town/apply", query, DRAWIO);

        assertEquals(
                first.body(),
                replay.body(),
                "Повтор request_id обязан вернуть прежний ответ: " + replay.body()
                        + " Иначе тест apply_RepeatedRequestId_ReplayWithoutSecondCall перестанет ловить эту "
                        + "поломку. При смене apply_RepeatedRequestId_ReplayWithoutSecondCall проверь повтор");
        assertEquals(
                1,
                gateway.applied().size(),
                "Повтор request_id не должен применяться к миру второй раз: " + gateway.applied()
                        + " Иначе тест apply_RepeatedRequestId_ReplayWithoutSecondCall перестанет ловить эту "
                        + "поломку. При смене apply_RepeatedRequestId_ReplayWithoutSecondCall проверь счётчик");
    }

    @Test
    void apply_SameRequestIdDifferentBody_ConflictedResponse() {
        send("POST", "/v1/town/apply", Map.of("request_id", "apply-conflict"), DRAWIO);

        RestResponse conflict = send(
                "POST",
                "/v1/town/apply",
                Map.of("request_id", "apply-conflict"),
                DRAWIO.replace("<mxGraphModel/>", "<mxGraphModel grid=\"0\"/>"));

        assertEquals(
                409,
                conflict.status(),
                "Тот же request_id с другим телом обязан давать 409: " + conflict.body()
                        + " Иначе тест apply_SameRequestIdDifferentBody_ConflictedResponse перестанет ловить эту "
                        + "поломку. При смене apply_SameRequestIdDifferentBody_ConflictedResponse проверь код");
        assertTrue(
                conflict.body().contains("conflict"),
                "Ответ 409 обязан называть код conflict: " + conflict.body()
                        + " Иначе тест apply_SameRequestIdDifferentBody_ConflictedResponse перестанет ловить эту "
                        + "поломку. При смене apply_SameRequestIdDifferentBody_ConflictedResponse проверь код");
    }

    @Test
    void apply_WithoutRequestId_UsesBodyFingerprint() {
        send("POST", "/v1/town/apply.drawio", DRAWIO);

        send("POST", "/v1/town/apply.drawio", DRAWIO);

        assertEquals(
                1,
                gateway.applied().size(),
                "Без request_id повтор того же .drawio обязан считаться повтором по отпечатку тела: "
                        + gateway.applied()
                        + " Иначе тест apply_WithoutRequestId_UsesBodyFingerprint перестанет ловить эту поломку. "
                        + "При смене apply_WithoutRequestId_UsesBodyFingerprint проверь идемпотентность");
    }

    @Test
    void apply_FailedValidation_AllowsRetry() {
        gateway.withFailure(new ApiException(
                ErrorCode.INVALID_PLAN, "участки 'barn' и 'storage' накладываются: разведите их по клеткам"));
        Map<String, String> query = Map.of("request_id", "apply-retry");
        RestResponse failed = send("POST", "/v1/town/apply", query, DRAWIO);

        gateway.withFailure(null);
        RestResponse retry = send("POST", "/v1/town/apply", query, DRAWIO);

        assertEquals(
                422,
                failed.status(),
                "Наложение участков обязано давать 422 invalid_plan: " + failed.body()
                        + " Иначе тест apply_FailedValidation_AllowsRetry перестанет ловить эту поломку. При "
                        + "смене apply_FailedValidation_AllowsRetry проверь код отказа");
        assertEquals(
                200,
                retry.status(),
                "После отказа плана тот же request_id обязан приниматься заново: " + retry.body()
                        + " Иначе тест apply_FailedValidation_AllowsRetry перестанет ловить эту поломку. При "
                        + "смене apply_FailedValidation_AllowsRetry проверь повтор");
    }
}
