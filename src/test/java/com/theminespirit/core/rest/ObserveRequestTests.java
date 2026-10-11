package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.ScanRadius;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class ObserveRequestTests {
    private static final String PATTERNS_16 = "[" + String.join(",", Collections.nCopies(16, "\"*_ore\"")) + "]";

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    static Stream<Arguments> validBodies() {
        ObserveRequest defaults = ObserveRequest.defaults();
        return Stream.of(
                Arguments.of("", defaults),
                Arguments.of("  \n\t ", defaults),
                Arguments.of("{}", defaults),
                Arguments.of("{\"radius\":1}", new ObserveRequest(ScanRadius.of(1), List.of(), true)),
                Arguments.of("{\"radius\":32}", new ObserveRequest(ScanRadius.of(32), List.of(), true)),
                Arguments.of("{\"filter\":[]}", defaults),
                Arguments.of("{\"filter\":[\"*_ore\",\"*_log\"]}",
                        new ObserveRequest(ScanRadius.DEFAULT, List.of("*_ore", "*_log"), true)),
                Arguments.of("{\"include_entities\":false}", new ObserveRequest(ScanRadius.DEFAULT, List.of(), false)),
                Arguments.of("{\"radius\":16,\"filter\":[\"stone\"],\"include_entities\":true}",
                        new ObserveRequest(ScanRadius.of(16), List.of("stone"), true)),
                Arguments.of("{\"filter\":" + PATTERNS_16 + "}",
                        new ObserveRequest(ScanRadius.DEFAULT, Collections.nCopies(16, "*_ore"), true)),
                Arguments.of("{\"filter\":[\"" + "a".repeat(64) + "\"]}",
                        new ObserveRequest(ScanRadius.DEFAULT, List.of("a".repeat(64)), true)));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("validBodies")
    void parse_ValidBody_ReturnsExpectedRequest(String body, ObserveRequest expected) {
        assertEquals(expected, ObserveRequest.parse(utf8(body)),
                "Допустимое тело observe обязано разбираться в ожидаемый запрос, пустое тело — в умолчания: " + body);
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("{", "строка"),
                Arguments.of("{\"radius\":8} x", "JSON"),
                Arguments.of("[]", "JSON-объектом"),
                Arguments.of("null", "JSON-объектом"),
                Arguments.of("\"radius\"", "JSON-объектом"),
                Arguments.of("{\"radius\":0}", "'radius' = 0"),
                Arguments.of("{\"radius\":33}", "'radius' = 33"),
                Arguments.of("{\"radius\":-5}", "'radius' = -5"),
                Arguments.of("{\"radius\":\"8\"}", "'radius' должно быть целым"),
                Arguments.of("{\"radius\":8.5}", "'radius' должно быть целым"),
                Arguments.of("{\"radius\":null}", "'radius' должно быть целым"),
                Arguments.of("{\"radius\":true}", "'radius' должно быть целым"),
                Arguments.of("{\"radius\":3000000000}", "'radius' должно быть целым"),
                Arguments.of("{\"filter\":\"*_ore\"}", "'filter' должно быть массивом"),
                Arguments.of("{\"filter\":[1]}", "filter[0]"),
                Arguments.of("{\"filter\":[\"ok\",\"\"]}", "filter[1]"),
                Arguments.of("{\"filter\":[\"Iron!\"]}", "IdFilter"),
                Arguments.of("{\"filter\":[" + String.join(",", Collections.nCopies(17, "\"a\"")) + "]}", "17 шаблонов"),
                Arguments.of("{\"filter\":[\"" + "a".repeat(65) + "\"]}", "длиной 65"),
                Arguments.of("{\"include_entities\":\"yes\"}", "'include_entities' должно быть"),
                Arguments.of("{\"foo\":1}", "Неизвестное поле 'foo'"),
                Arguments.of("{\"radius\":8,\"radius\":9}", "JSON"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidBodies")
    void parse_InvalidBody_ThrowsInvalidRequestNamingTheProblem(String body, String mustContain) {
        ApiException error = assertThrows(ApiException.class, () -> ObserveRequest.parse(utf8(body)),
                "Неверное тело observe обязано давать invalid_request, а не молча приниматься: " + body);

        assertEquals(ErrorCode.INVALID_REQUEST, error.error().code(),
                "Ошибка тела запроса — invalid_request (400): " + body);
        assertTrue(error.getMessage().contains(mustContain),
                "Сообщение называет поле и проблему ('" + mustContain + "'), чтобы клиент мог исправить запрос: "
                        + error.getMessage());
    }

    @Test
    void parse_UnknownField_DetailsNameFieldAndAllowedFields() {
        ApiException error = assertThrows(ApiException.class, () -> ObserveRequest.parse(utf8("{\"foo\":1}")),
                "Неизвестное поле обязано отклоняться");

        assertEquals("foo", error.error().details().get("field"), "details.field называет лишнее поле");
        assertEquals(List.of("radius", "filter", "include_entities"), error.error().details().get("allowed"),
                "details.allowed перечисляет допустимые поля в порядке документации");
    }

    @Test
    void parse_FilterPatternWithForbiddenSymbol_DetailsNameIdFilterAndIndex() {
        ApiException error = assertThrows(ApiException.class,
                () -> ObserveRequest.parse(utf8("{\"filter\":[\"ok\",\"Iron!\"]}")),
                "Шаблон с запрещённым символом обязан отклоняться, а не молча уходить в скан");

        assertEquals(ErrorCode.INVALID_REQUEST, error.error().code(), "Отвергнутый шаблон — invalid_request (400)");
        assertEquals(1, error.error().details().get("index"),
                "details.index называет номер плохого шаблона filter[1], чтобы клиент нашёл поле: "
                        + error.error().details());
        assertTrue(error.getMessage().contains("IdFilter"),
                "Сообщение обязано называть правило IdFilter, которым проверен шаблон: " + error.getMessage());
    }

    @Test
    void parse_BrokenJson_ErrorCarriesLineAndColumn() {
        ApiException error = assertThrows(ApiException.class, () -> ObserveRequest.parse(utf8("{\n  \"radius\": }")),
                "Битый JSON обязан отклоняться");

        assertEquals(2, error.error().details().get("line"), "details.line называет строку ошибки разбора");
        assertTrue(error.getMessage().contains("строка 2"), "Сообщение называет позицию ошибки: " + error.getMessage());
    }

    @Test
    void parse_InvalidUtf8Bytes_ThrowsInvalidRequest() {
        byte[] body = {'{', '"', (byte) 0xFF, '"', ':', '1', '}'};

        ApiException error = assertThrows(ApiException.class, () -> ObserveRequest.parse(body),
                "Байты вне UTF-8 в теле обязаны давать invalid_request, а не необработанное исключение");

        assertEquals(ErrorCode.INVALID_REQUEST, error.error().code(), "Ошибка кодировки — invalid_request (400)");
    }
}
