package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class RouterProperties {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ERROR_CODES = Set.of("invalid_request", "not_found");
    private static final Router ROUTER = Router.of(List.of(
            RouterTests.route("GET", "/v1/capabilities", "capabilities"),
            RouterTests.route("GET", "/v1/bots", "bots"),
            RouterTests.route("GET", "/v1/bots/{id}", "bot"),
            RouterTests.route("POST", "/v1/bots/{id}/observe", "observe")));

    @Provide
    Arbitrary<String> pathsFromKnownPieces() {
        Arbitrary<String> piece = Arbitraries.of("", "v1", "bots", "observe", "capabilities", "..", ".", "%00",
                "a b", "дух", "abc", "A_b-9");
        return piece.list().ofMaxSize(6).map(parts -> "/" + String.join("/", parts));
    }

    @Provide
    Arbitrary<String> httpMethods() {
        return Arbitraries.of("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS", "get", "");
    }

    @Property
    void handle_AnyMethodAndPath_NeverThrowsAndAnswersWithValidJson(@ForAll String method, @ForAll String path)
            throws IOException {
        assertValidAnswer(ROUTER.handle(new RestRequest(method, path, Map.of(), Map.of(), new byte[0])), method, path);
    }

    @Property
    void handle_KnownPathPiecesAndMethods_NeverThrowsAndAnswersWithValidJson(
            @ForAll("pathsFromKnownPieces") String path, @ForAll("httpMethods") String method) throws IOException {
        assertValidAnswer(ROUTER.handle(new RestRequest(method, path, Map.of(), Map.of(), new byte[0])), method, path);
    }

    private static void assertValidAnswer(RestResponse response, String method, String path) throws IOException {
        JsonNode body = MAPPER.readTree(response.body());
        assertTrue(Set.of(200, 404, 405).contains(response.status()),
                "Маршрутизатор отвечает только 200, 404 или 405: " + method + " " + path + " дал " + response.status());
        if (response.status() == 200) {
            assertTrue(body.has("handled"), "Успешный ответ принадлежит обработчику: " + response.body());
        } else {
            assertTrue(ERROR_CODES.contains(body.path("error").path("code").asText()),
                    "Ошибка маршрутизации обязана нести код not_found или invalid_request: " + response.body());
            assertTrue(body.path("error").path("details").isObject(),
                    "details всегда объект: " + response.body());
        }
    }
}
