package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RouterTests {
    private static final String ID_64 = "a".repeat(64);
    private static final String TRANSFER_ID =
            "transfer:graph:craft_stone_pickaxe@c48663b4-da59-43d9-9804-81a680580300:stick:"
                    + "caf11c6f-9a9a-408d-92ed-71726c762c19";
    private static final Router ROUTER = Router.of(List.of(
            route("GET", "/v1/capabilities", "capabilities"),
            route("GET", "/v1/bots", "bots"),
            route("GET", "/v1/bots/{id}", "bot"),
            route("POST", "/v1/bots/{id}/observe", "observe")));

    static Route route(String method, String pattern, String name) {
        return new Route(
                method,
                pattern,
                (request, params) -> RestResponse.ok("{\"handled\":\"" + name + "\",\"id\":\""
                        + params.values().getOrDefault("id", "") + "\"}"));
    }

    static RestRequest request(String method, String path, byte[] body) {
        return new RestRequest(method, path, Map.of(), Map.of(), body);
    }

    static Stream<Arguments> table() {
        return Stream.of(
                Arguments.of("GET", "/v1/capabilities", 200, "{\"handled\":\"capabilities\",\"id\":\"\"}", null),
                Arguments.of("GET", "/v1/bots", 200, "{\"handled\":\"bots\",\"id\":\"\"}", null),
                Arguments.of("GET", "/v1/bots/abc", 200, "{\"handled\":\"bot\",\"id\":\"abc\"}", null),
                Arguments.of("GET", "/v1/bots/a_b-C9", 200, "{\"handled\":\"bot\",\"id\":\"a_b-C9\"}", null),
                Arguments.of(
                        "GET",
                        "/v1/bots/graph:craft_stone_axe@c48663b4-da59-43d9-9804-81a680580300",
                        200,
                        "{\"handled\":\"bot\",\"id\":\"graph:craft_stone_axe@c48663b4-da59-43d9-9804-81a680580300\"}",
                        null),
                Arguments.of("GET", "/v1/bots/" + ID_64, 200, "{\"handled\":\"bot\",\"id\":\"" + ID_64 + "\"}", null),
                Arguments.of(
                        "GET",
                        "/v1/bots/" + TRANSFER_ID,
                        200,
                        "{\"handled\":\"bot\",\"id\":\"" + TRANSFER_ID + "\"}",
                        null),
                Arguments.of("POST", "/v1/bots/abc/observe", 200, "{\"handled\":\"observe\",\"id\":\"abc\"}", null),
                Arguments.of(
                        "GET", "/v1/bots/" + "a".repeat(RoutePattern.PARAM_MAX_LENGTH + 1), 404, "not_found", null),
                Arguments.of("GET", "/v1/bots/", 404, "not_found", null),
                Arguments.of("GET", "/v1/bots//", 404, "not_found", null),
                Arguments.of("GET", "//v1/bots", 404, "not_found", null),
                Arguments.of("GET", "/v1//bots", 404, "not_found", null),
                Arguments.of("GET", "v1/bots", 404, "not_found", null),
                Arguments.of("GET", "/", 404, "not_found", null),
                Arguments.of("GET", "", 404, "not_found", null),
                Arguments.of("GET", "/V1/bots", 404, "not_found", null),
                Arguments.of("GET", "/v1/Bots", 404, "not_found", null),
                Arguments.of("GET", "/v1/bots/..", 404, "not_found", null),
                Arguments.of("GET", "/v1/bots/a/../b", 404, "not_found", null),
                Arguments.of("GET", "/v1/bots/%00", 404, "not_found", null),
                Arguments.of("GET", "/v1/bots/a%2Fb", 404, "not_found", null),
                Arguments.of("GET", "/v1/bots/a b", 404, "not_found", null),
                Arguments.of("GET", "/v1/bots/дух", 404, "not_found", null),
                Arguments.of("GET", "/v1/bots/abc/observe/x", 404, "not_found", null),
                Arguments.of("GET", "/v1/nope", 404, "not_found", null),
                Arguments.of("POST", "/v1/bots", 405, "invalid_request", "GET"),
                Arguments.of("DELETE", "/v1/bots/abc", 405, "invalid_request", "GET"),
                Arguments.of("GET", "/v1/bots/abc/observe", 405, "invalid_request", "POST"),
                Arguments.of("HEAD", "/v1/capabilities", 405, "invalid_request", "GET"),
                Arguments.of("get", "/v1/bots", 405, "invalid_request", "GET"),
                Arguments.of("OPTIONS", "/v1/bots/abc", 405, "invalid_request", "GET"));
    }

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @MethodSource("table")
    void handle_TableCase_ProducesDocumentedStatusBodyAndAllow(
            String method, String path, int status, String expected, String allow) {
        RestResponse response = ROUTER.handle(request(method, path, new byte[0]));

        assertEquals(
                status,
                response.status(),
                "Маршрутизация " + method + " " + path + " обязана дать статус " + status + ": неизвестный путь — 404, "
                        + "чужой метод — 405, иначе клиент получит чужой обработчик. Тело: " + response.body());
        if (status == 200) {
            assertEquals(
                    expected,
                    response.body(),
                    "Обработчик и параметр пути для " + method + " " + path + " определены неверно");
        } else {
            assertTrue(
                    response.body().startsWith("{\"error\":{\"code\":\"" + expected + "\""),
                    "Ошибка маршрутизации " + method + " " + path + " обязана нести код " + expected
                            + " в объекте ошибки, а не пустой ответ. Тело: " + response.body());
        }
        assertEquals(
                allow,
                response.headers().get("Allow"),
                "Заголовок Allow при 405 перечисляет допустимые методы пути (и отсутствует иначе): " + method + " "
                        + path);
    }

    @Test
    void handle_PathWithTwoMethods_AllowListsBothSortedAlphabetically() {
        Router router = Router.of(List.of(route("POST", "/v1/x", "post"), route("GET", "/v1/x", "get")));

        RestResponse response = router.handle(request("DELETE", "/v1/x", new byte[0]));

        assertEquals(
                "GET, POST",
                response.headers().get("Allow"),
                "Allow обязан перечислять все методы пути по алфавиту: клиент выбирает по нему допустимый метод");
    }

    @Test
    void handle_BodyOfExactLimit_ReachesHandler() {
        RestResponse response =
                ROUTER.handle(request("POST", "/v1/bots/abc/observe", new byte[RestTuning.MAX_BODY_BYTES]));

        assertEquals(
                200,
                response.status(),
                "Тело ровно в лимит MAX_BODY_BYTES допустимо: отказ на границе ломает честные запросы");
    }

    @Test
    void handle_BodyOverLimit_Returns400InvalidRequestBeforeHandler() {
        RestResponse response =
                ROUTER.handle(request("POST", "/v1/bots/abc/observe", new byte[RestTuning.MAX_BODY_BYTES + 1]));

        assertEquals(400, response.status(), "Тело больше лимита обязано давать 400, а не доходить до обработчика");
        assertTrue(
                response.body().contains("\"code\":\"invalid_request\"")
                        && response.body().contains(String.valueOf(RestTuning.MAX_BODY_BYTES)),
                "Ошибка называет код invalid_request и лимит в байтах, чтобы клиент знал, что сократить: "
                        + response.body());
    }

    @Test
    void handle_HandlerThrowsApiException_ReturnsItsErrorObject() {
        Router router = Router.of(List.of(new Route("GET", "/v1/x", (request, params) -> {
            throw new ApiException(ErrorCode.CONFLICT, "занято");
        })));

        RestResponse response = router.handle(request("GET", "/v1/x", new byte[0]));

        assertEquals(409, response.status(), "ApiException обработчика обязан стать ответом со статусом его кода");
        assertEquals(
                "{\"error\":{\"code\":\"conflict\",\"message\":\"занято\",\"details\":{}}}",
                response.body(),
                "Тело ответа — объект ошибки исключения");
    }

    @Test
    void of_DuplicateMethodAndPattern_Rejected() {
        List<Route> duplicates = List.of(route("GET", "/v1/x", "a"), route("GET", "/v1/x", "b"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> Router.of(duplicates),
                "Два обработчика одного запроса — ошибка сборки маршрутов: победитель определялся бы порядком списка");
        assertTrue(
                error.getMessage().contains("GET /v1/x"),
                "Сообщение называет дублирующийся маршрут: " + error.getMessage());
    }

    @ParameterizedTest
    @MethodSource("badPatterns")
    void route_MalformedPattern_Rejected(String pattern) {
        assertThrows(
                IllegalArgumentException.class,
                () -> route("GET", pattern, "x"),
                "Шаблон '" + pattern + "' неверен и обязан отклоняться при сборке маршрута, а не молча не совпадать");
    }

    static Stream<String> badPatterns() {
        return Stream.of("v1", "/v1/", "/v1//x", "/v1/{Id}", "/v1/x{id}", "/v1/{id");
    }

    @Test
    void route_LowerCaseMethod_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> route("get", "/v1/x", "x"),
                "Метод маршрута задаётся в верхнем регистре: методы запроса сравниваются точно");
    }

    @Test
    void handle_Utf8BodyBytes_PassedToHandlerUnchanged() {
        byte[] body = "{\"a\":\"я\"}".getBytes(StandardCharsets.UTF_8);
        Router router = Router.of(List.of(new Route(
                "POST", "/v1/x", (request, params) -> RestResponse.ok(String.valueOf(request.body().length)))));

        RestResponse response = router.handle(request("POST", "/v1/x", body));

        assertEquals(
                String.valueOf(body.length),
                response.body(),
                "Обработчик получает байты тела как есть: раскодирование UTF-8 делает он сам");
    }
}
