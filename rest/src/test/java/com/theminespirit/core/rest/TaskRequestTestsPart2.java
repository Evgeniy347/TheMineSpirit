package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.api.ConditionCatalog;
import com.theminespirit.core.script.api.ScriptCatalog;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

class TaskRequestTestsPart2 {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String UUID_TEXT = "3f1c2d3e-0000-4000-8000-000000000000";
    private static final ScriptCatalog CATALOG =
            CoreScripts.defaultCatalog().restrictedTo(Set.of("scan", "goto", "follow", "collect_drops", "wait"));
    private static final WorldObservation OBSERVATION = new WorldObservation(
            "bot", 1, new BlockCoord(0, 64, 0), List.of(), List.of(), List.of(), List.of(), ScanRadius.DEFAULT.value());

    private static byte[] body(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static String request(String extra) {
        return "{\"request_id\":\"" + UUID_TEXT + "\"" + extra + "}";
    }

    static Stream<Arguments> table() {
        return Stream.of(
                Arguments.of(request(""), 400, "goal или plan"),
                Arguments.of(request(",\"goal\":\"срубить деревья\""), 200, ""),
                Arguments.of(request(",\"plan\":{\"type\":\"script\",\"script\":\"scan\"}"), 200, ""),
                Arguments.of(request(",\"goal\":\"цель\",\"plan\":{\"type\":\"script\",\"script\":\"scan\"}"), 200, ""),
                Arguments.of("{\"goal\":\"цель\"}", 400, "request_id"),
                Arguments.of("{\"request_id\":123,\"goal\":\"цель\"}", 400, "request_id"),
                Arguments.of("{\"request_id\":\"не-uuid\",\"goal\":\"цель\"}", 400, "не является UUID"),
                Arguments.of("{\"request_id\":\"" + UUID_TEXT + "\"}", 400, "goal или plan"),
                Arguments.of(request(",\"goal\":null,\"plan\":{\"type\":\"script\",\"script\":\"scan\"}"), 200, ""),
                Arguments.of(request(",\"plan\":null,\"goal\":\"цель\""), 200, ""),
                Arguments.of(request(",\"goal\":123"), 400, "'goal' должно быть строкой"),
                Arguments.of(request(",\"goal\":\"" + "а".repeat(500) + "\""), 200, ""),
                Arguments.of(request(",\"goal\":\"" + "а".repeat(501) + "\""), 400, "длиннее 500"),
                Arguments.of(request(",\"goal\":\"цель\",\"plan\":[]"), 400, "'plan' должно быть JSON-объектом"),
                Arguments.of(request(",\"goal\":\"цель\",\"plan\":\"goto\""), 400, "'plan' должно быть JSON-объектом"),
                Arguments.of(request(",\"goal\":\"цель\",\"params\":[]"), 400, "'params' должно быть JSON-объектом"),
                Arguments.of(request(",\"goal\":\"цель\",\"params\":{\"target\":\"Steve\"}"), 200, ""),
                Arguments.of(request(",\"goal\":\"цель\",\"params\":{\"radius\":3}"), 200, ""),
                Arguments.of(request(",\"goal\":\"цель\",\"params\":{\"bad\":1.5}"), 400, "строкой или целым"),
                Arguments.of(request(",\"goal\":\"цель\",\"params\":{\"bad\":true}"), 400, "строкой или целым"),
                Arguments.of(request(",\"goal\":\"цель\",\"params\":{\"bad\":null}"), 400, "строкой или целым"),
                Arguments.of(request(",\"goal\":\"цель\",\"params\":{\"bad\":[1]}"), 400, "строкой или целым"),
                Arguments.of(request(",\"goal\":\"цель\",\"foo\":1"), 400, "Неизвестное поле 'foo'"),
                Arguments.of(request(",\"goal\":\"цель\",\"params\":{\"target\":\"Стив\"}"), 200, ""),
                Arguments.of(
                        "{\"request_id\":\"" + UUID_TEXT + "\",\"goal\":\"с рубкой\",\"params\":{\"x\":1}}", 200, ""),
                Arguments.of("{", 400, "не разобрано как JSON"),
                Arguments.of("[1]", 400, "JSON-объектом"),
                Arguments.of("", 400, "JSON-объектом"),
                Arguments.of(
                        request(",\"goal\":\"цель\",\"request_id\":\"" + UUID_TEXT + "\""), 400, "Duplicate field"));
    }

    @Provide
    Arbitrary<String> jsonBodies() {
        return Arbitraries.oneOf(
                Arbitraries.strings()
                        .withChars('{', '}', '"', ':', ',', 'а', '1')
                        .ofMinLength(0)
                        .ofMaxLength(80),
                Arbitraries.of(
                        request(",\"goal\":\"цель\""),
                        request(",\"plan\":{\"type\":\"script\",\"script\":\"scan\"}"),
                        "{\"request_id\":\"" + UUID_TEXT + "\",\"goal\":\"x\",\"params\":{\"target\":\"a\"}}",
                        "{\"foo\":1}",
                        "[1]",
                        "null",
                        "\"строка\"",
                        ""));
    }

    @Property(tries = 200)
    void parse_AnyBody_ThrowsNothingButApiException(@ForAll("jsonBodies") String json) {
        try {
            TaskRequest.parse(body(json));
        } catch (ApiException expected) {
            assertTrue(
                    expected.error().code() == ErrorCode.INVALID_REQUEST
                            || expected.error().code() == ErrorCode.INVALID_PLAN,
                    "Parse обязан отказывать только invalid_request/invalid_plan, получен "
                            + expected.error().code()
                            + " Иначе тест parse_AnyBody_ThrowsNothingButApiException перестанет ловить эту поломку. При смене parse_AnyBody_ThrowsNothingButApiException проверь это утверждение.");
        }
    }

    @Property(tries = 100)
    void planGoal_AnyRequestAndObservation_ThrowsNothingButApiException(@ForAll("jsonBodies") String json) {
        PlanningService service = new PlanningService(CATALOG, ConditionCatalog.empty());
        TaskRequest request;
        try {
            request = TaskRequest.parse(body(json));
        } catch (ApiException rejected) {
            return;
        }
        try {
            if (request.goal() != null && !request.goal().isBlank()) {
                service.planGoal(request, OBSERVATION);
            } else if (request.plan() != null) {
                service.validatePlan(request.plan());
            }
        } catch (ApiException expected) {
            assertTrue(
                    expected.error() != null,
                    "ApiException всегда несёт готовый объект ошибки"
                            + " Иначе тест planGoal_AnyRequestAndObservation_ThrowsNothingButApiException перестанет ловить эту поломку. При смене planGoal_AnyRequestAndObservation_ThrowsNothingButApiException проверь это утверждение.");
        } catch (RuntimeException other) {
            fail("Планировщик обязан бросать только ApiException, а получил " + other);
        }
    }

    @Test
    void validatePlan_SequenceScanGoto_ParsesWholeTree() throws Exception {
        PlanningService service = new PlanningService(CATALOG, ConditionCatalog.empty());
        JsonNode plan = MAPPER.readTree("{\"type\":\"sequence\",\"steps\":["
                + "{\"type\":\"script\",\"script\":\"scan\",\"params\":{\"radius\":8}},"
                + "{\"type\":\"script\",\"script\":\"goto\",\"params\":{\"x\":1,\"y\":64,\"z\":2}}]}");

        TaskNode parsed = service.validatePlan(plan);

        assertTrue(
                parsed instanceof SequenceNode,
                "Последовательность обязана разбираться в SequenceNode"
                        + " Иначе тест validatePlan_SequenceScanGoto_ParsesWholeTree перестанет ловить эту поломку. При смене validatePlan_SequenceScanGoto_ParsesWholeTree проверь это утверждение.");
    }

    @Test
    void requestId_ParsedValue_MatchesUuidInBody() {
        TaskRequest parsed = TaskRequest.parse(body(request(",\"goal\":\"цель\"")));

        assertEquals(
                UUID.fromString(UUID_TEXT),
                parsed.requestId(),
                "request_id обязан разбираться в UUID дословно: он склеивает повтор запроса"
                        + " Иначе тест requestId_ParsedValue_MatchesUuidInBody перестанет ловить эту поломку. При смене requestId_ParsedValue_MatchesUuidInBody проверь это утверждение.");
        assertInstanceOf(
                UUID.class,
                parsed.requestId(),
                "request_id — UUID, а не строка"
                        + " Иначе тест requestId_ParsedValue_MatchesUuidInBody перестанет ловить эту поломку. При смене requestId_ParsedValue_MatchesUuidInBody проверь это утверждение.");
    }
}
