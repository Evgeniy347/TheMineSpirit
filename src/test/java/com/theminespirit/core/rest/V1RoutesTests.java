package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.ItemCount;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.script.ScriptCatalog;
import com.theminespirit.core.script.ScriptSpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class V1RoutesTests {
    private static final String IDLE_ID = "11111111-1111-1111-1111-111111111111";
    private static final String BUSY_ID = "22222222-2222-2222-2222-222222222222";
    private static final String DEAD_ID = "33333333-3333-3333-3333-333333333333";
    private static final List<BotInfo> BOTS = List.of(
            new BotInfo(IDLE_ID, "spirit", BotState.IDLE, 10, 64, -3, "minecraft:overworld", null),
            new BotInfo(BUSY_ID, "Дух-2", BotState.BUSY, 0, 70, 5, "minecraft:the_nether", null),
            new BotInfo(DEAD_ID, "spirit", BotState.DEAD, 1, 2, 3, "minecraft:overworld", null));
    private static final ScriptCatalog CATALOG = ScriptCatalog.of(List.of(
            new ScriptSpec("scan", "просканировать", List.of(), List.of(), List.of())));
    private static final WorldObservation OBSERVATION = new WorldObservation(IDLE_ID, 100, new BlockCoord(10, 64, -3),
            List.of(), List.of(new ItemCount("minecraft:stone", 5)), List.of(), List.of(), 8);

    private static FakeBotGateway gateway() {
        return new FakeBotGateway(BOTS).observing(OBSERVATION);
    }

    private static RestResponse send(FakeBotGateway gateway, String method, String path, String body) {
        Router router = Router.of(V1Routes.routes(gateway, CATALOG));
        return router.handle(new RestRequest(method, path, Map.of(), Map.of(), body.getBytes(StandardCharsets.UTF_8)));
    }

    static Stream<Arguments> table() {
        String observe = "/v1/bots/" + IDLE_ID + "/observe";
        return Stream.of(
                Arguments.of("GET", "/v1/capabilities", "", 200, "\"id\":\"scan\""),
                Arguments.of("GET", "/v1/bots", "", 200, "\"bots\":[{\"id\":\"" + IDLE_ID + "\""),
                Arguments.of("GET", "/v1/bots/" + IDLE_ID, "", 200, "\"state\":\"idle\""),
                Arguments.of("GET", "/v1/bots/" + BUSY_ID, "", 200, "\"state\":\"busy\""),
                Arguments.of("GET", "/v1/bots/" + DEAD_ID, "", 200, "\"state\":\"dead\""),
                Arguments.of("GET", "/v1/bots/" + BUSY_ID, "", 200, "\"dimension\":\"minecraft:the_nether\""),
                Arguments.of("GET", "/v1/bots/unknown", "", 404, "Дух 'unknown' не найден"),
                Arguments.of("GET", "/v1/bots/unknown", "", 404, "\"details\":{\"id\":\"unknown\"}"),
                Arguments.of("POST", observe, "", 200, "\"bot\":\"" + IDLE_ID + "\""),
                Arguments.of("POST", observe, "{}", 200, "\"hash\":\""),
                Arguments.of("POST", observe, "{\"radius\":8,\"include_entities\":false}", 200, "\"radius\":8"),
                Arguments.of("POST", observe, "{\"radius\":99}", 400, "'radius' = 99"),
                Arguments.of("POST", observe, "{\"radius\":0}", 400, "\"code\":\"invalid_request\""),
                Arguments.of("POST", observe, "{\"radius\":\"8\"}", 400, "'radius' должно быть целым"),
                Arguments.of("POST", observe, "{\"foo\":1}", 400, "Неизвестное поле 'foo'"),
                Arguments.of("POST", observe, "{", 400, "строка 1"),
                Arguments.of("POST", observe, "[1]", 400, "JSON-объектом"),
                Arguments.of("POST", "/v1/bots/unknown/observe", "{}", 404, "\"code\":\"not_found\""),
                Arguments.of("GET", observe, "", 405, "Метод 'GET' не поддерживается"),
                Arguments.of("POST", "/v1/bots", "", 405, "допустимо GET"),
                Arguments.of("POST", "/v1/capabilities", "", 405, "допустимо GET"),
                Arguments.of("DELETE", "/v1/bots/" + IDLE_ID, "", 405, "\"code\":\"invalid_request\""),
                Arguments.of("GET", "/v1/nope", "", 404, "\"code\":\"not_found\""),
                Arguments.of("GET", "/v1/bots/" + IDLE_ID + "/", "", 404, "\"code\":\"not_found\""));
    }

    @ParameterizedTest(name = "{0} {1} {2} -> {3}")
    @MethodSource("table")
    void route_TableCase_ReturnsExpectedStatusAndBodyFragment(String method, String path, String body, int status,
            String fragment) {
        RestResponse response = send(gateway(), method, path, body);

        assertEquals(status, response.status(),
                "Обработчик v1 для " + method + " " + path + " обязан дать статус " + status + ": тело " + response.body());
        assertTrue(response.body().contains(fragment),
                "Тело ответа на " + method + " " + path + " обязано содержать '" + fragment + "': " + response.body());
    }

    @Test
    void bots_ThreeSpirits_ListEqualsReferenceJson() {
        RestResponse response = send(gateway(), "GET", "/v1/bots", "");

        assertEquals("{\"bots\":["
                        + "{\"id\":\"" + IDLE_ID + "\",\"name\":\"spirit\",\"state\":\"idle\",\"position\":[10,64,-3],"
                        + "\"dimension\":\"minecraft:overworld\",\"current_task\":null},"
                        + "{\"id\":\"" + BUSY_ID + "\",\"name\":\"Дух-2\",\"state\":\"busy\",\"position\":[0,70,5],"
                        + "\"dimension\":\"minecraft:the_nether\",\"current_task\":null},"
                        + "{\"id\":\"" + DEAD_ID + "\",\"name\":\"spirit\",\"state\":\"dead\",\"position\":[1,2,3],"
                        + "\"dimension\":\"minecraft:overworld\",\"current_task\":null}]}",
                response.body(),
                "Список духов — контракт REST: id, name, state, position [x,y,z], dimension и current_task "
                        + "(null до чеклиста 15) в этом порядке");
    }

    @Test
    void bots_NoSpirits_ReturnsEmptyList() {
        RestResponse response = send(new FakeBotGateway(List.of()), "GET", "/v1/bots", "");

        assertEquals("{\"bots\":[]}", response.body(), "Без духов список пуст, а не 404: коллекция существует всегда");
    }

    @Test
    void observe_FullBody_PassesParsedRequestToGateway() {
        FakeBotGateway gateway = gateway();

        send(gateway, "POST", "/v1/bots/" + IDLE_ID + "/observe",
                "{\"radius\":16,\"filter\":[\"*_ore\"],\"include_entities\":false}");

        assertEquals(List.of(new ObserveRequest(ScanRadius.of(16), List.of("*_ore"), false)), gateway.observeRequests(),
                "Шлюз обязан получить разобранный запрос целиком: радиус, фильтр и include_entities");
    }

    @Test
    void observe_ValidRequest_ResponseIsObservationJson() {
        RestResponse response = send(gateway(), "POST", "/v1/bots/" + IDLE_ID + "/observe", "{}");

        assertEquals(OBSERVATION.toJson(), response.body(),
                "Ответ observe — наблюдение в формате docs/API.md без обёрток: так его читает ИИ-планировщик");
    }

    @Test
    void observe_InvalidRequest_NeverReachesGateway() {
        FakeBotGateway gateway = gateway();

        send(gateway, "POST", "/v1/bots/" + IDLE_ID + "/observe", "{\"radius\":99}");

        assertEquals(List.of(), gateway.observeRequests(),
                "Неверный запрос отклоняется до обращения к серверному потоку: ошибка клиента не должна нагружать тик");
    }

    @Test
    void route_GatewayThrowsInternal_Returns500WithSameErrorObject() {
        ApiException failure = new ApiException(ErrorCode.INTERNAL, "Сервер не ответил за 5 с", Map.of("timeout_ms", 5000));
        FakeBotGateway gateway = gateway().failingWith(failure);

        RestResponse bots = send(gateway, "GET", "/v1/bots", "");
        RestResponse observe = send(gateway, "POST", "/v1/bots/" + IDLE_ID + "/observe", "{}");

        String expected = "{\"error\":{\"code\":\"internal\",\"message\":\"Сервер не ответил за 5 с\","
                + "\"details\":{\"timeout_ms\":5000}}}";
        assertEquals(500, bots.status(), "internal обязан быть 500 в списке духов");
        assertEquals(expected, bots.body(), "Ошибка шлюза уходит клиенту тем же объектом в списке духов");
        assertEquals(500, observe.status(), "internal обязан быть 500 в observe");
        assertEquals(expected, observe.body(), "Ошибка шлюза уходит клиенту тем же объектом в observe");
    }

    @Test
    void routes_V1_AreAddableWithoutChangingExistingOnes() {
        Route extra = new Route("GET", "/v1/tasks/{id}", (request, params) -> RestResponse.ok("{\"task\":true}"));
        List<Route> all = new ArrayList<>(V1Routes.routes(gateway(), CATALOG));
        all.add(extra);

        RestResponse response = Router.of(all).handle(new RestRequest("GET", "/v1/tasks/t-1", Map.of(), Map.of(),
                new byte[0]));

        assertEquals("{\"task\":true}", response.body(),
                "Чеклист 15 обязан добавлять маршруты списком к V1Routes без правки существующих классов");
    }
}
