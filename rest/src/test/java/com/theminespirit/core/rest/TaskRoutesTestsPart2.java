package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.api.ConditionCatalog;
import com.theminespirit.core.script.api.ScriptCatalog;
import com.theminespirit.core.task.TaskRegistry;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

class TaskRoutesTestsPart2 {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String BOT = "6f1c2d3e-0000-4000-8000-000000000001";
    private static final ScriptCatalog CATALOG =
            CoreScripts.defaultCatalog().restrictedTo(Set.of("scan", "goto", "follow", "collect_drops", "wait"));
    private static final WorldObservation OBSERVATION = new WorldObservation(
            BOT, 1, new BlockCoord(0, 64, 0), List.of(), List.of(), List.of(), List.of(), ScanRadius.DEFAULT.value());

    private TaskRegistry registry;
    private FakeTaskGateway gateway;
    private PlanningService planning;
    private Idempotency idempotency;
    private SseSlots slots;
    private final FakeBoardGateway board = new FakeBoardGateway();
    private Router router;

    @BeforeEach
    void setUp() {
        registry = new TaskRegistry();
        planning = new PlanningService(CATALOG, ConditionCatalog.empty());
        gateway = new FakeTaskGateway().observing(OBSERVATION).planning(planning);
        idempotency = new IdempotencyCache();
        slots = new SseSlots();
        router = Router.of(new TaskRoutes(registry, gateway, planning, idempotency, slots, board).routes());
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private RestResponse send(String method, String path, String body, Map<String, String> headers) {
        return router.handle(new RestRequest(method, path, Map.of(), headers, utf8(body)));
    }

    private RestResponse send(String method, String path, String body) {
        return send(method, path, body, Map.of());
    }

    private String postTasks(String goalJson) {
        String body = "{\"request_id\":\"" + uuid() + "\"," + goalJson + "}";
        RestResponse response = send("POST", "/v1/bots/" + BOT + "/tasks", body);
        assertEquals(
                202,
                response.status(),
                "Постановка задачи обязана приниматься: тело " + response.body()
                        + " Иначе тест observing перестанет ловить эту поломку. При смене observing проверь это утверждение.");
        return response.body();
    }

    private String taskId(String acceptedBody) throws Exception {
        return MAPPER.readTree(acceptedBody).get("task_id").asText();
    }

    static Stream<Arguments> table() {
        return Stream.of(
                Arguments.of(
                        "POST",
                        "tasks",
                        "{\"request_id\":\"" + uuid()
                                + "\",\"plan\":{\"type\":\"script\",\"script\":\"goto\",\"params\":{\"x\":1,\"y\":64,\"z\":2}}}",
                        202,
                        "\"state\":\"accepted\""),
                Arguments.of(
                        "POST",
                        "tasks",
                        "{\"request_id\":\"" + uuid()
                                + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}",
                        202,
                        "\"state\":\"accepted\""),
                Arguments.of("POST", "tasks", "{", 400, "не разобрано как JSON"),
                Arguments.of("POST", "tasks", "{\"request_id\":\"" + uuid() + "\"}", 400, "goal или plan"),
                Arguments.of("POST", "tasks", "{\"request_id\":\"не-uuid\",\"goal\":\"цель\"}", 400, "request_id"),
                Arguments.of(
                        "POST",
                        "tasks",
                        "{\"request_id\":\"" + uuid() + "\",\"goal\":\"цель\",\"foo\":1}",
                        400,
                        "'foo'"),
                Arguments.of(
                        "POST",
                        "tasks",
                        "{\"request_id\":\"" + uuid() + "\",\"goal\":\"спой гимн\"}",
                        422,
                        "не распознана"),
                Arguments.of(
                        "POST",
                        "tasks",
                        "{\"request_id\":\"" + uuid() + "\",\"plan\":{\"type\":\"script\",\"script\":\"return_home\"}}",
                        422,
                        "return_home"),
                Arguments.of(
                        "POST",
                        "plan",
                        "{\"plan\":{\"type\":\"script\",\"script\":\"goto\","
                                + "\"params\":{\"x\":1,\"y\":64,\"z\":2}}}",
                        200,
                        "\"valid\":true"),
                Arguments.of(
                        "POST",
                        "plan",
                        "{\"plan\":{\"type\":\"script\",\"script\":\"return_home\"}}",
                        422,
                        "return_home"),
                Arguments.of("POST", "plan", "{\"plan\":{\"type\":\"script\"}}", 422, "script"),
                Arguments.of("POST", "plan", "{}", 400, "нет поля 'plan'"),
                Arguments.of("POST", "plan", "[1]", 400, "JSON-объектом"),
                Arguments.of("POST", "plan", "{", 400, "не разобрано как JSON"));
    }

    private static String jsonField(String json, String field) {
        try {
            return MAPPER.readTree(json).get(field).asText();
        } catch (Exception e) {
            throw new IllegalStateException("Тестовое тело запроса обязано быть валидным JSON: " + json, e);
        }
    }

    @Test
    void create_SameRequestIdDifferentBody_Conflicts() {
        String first =
                "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}";
        send("POST", "/v1/bots/" + BOT + "/tasks", first);
        String other = "{\"request_id\":\"" + jsonField(first, "request_id")
                + "\",\"plan\":{\"type\":\"script\",\"script\":\"goto\",\"params\":{\"x\":1,\"y\":64,\"z\":2}}}";

        RestResponse response = send("POST", "/v1/bots/" + BOT + "/tasks", other);

        assertEquals(
                409,
                response.status(),
                "Тот же request_id с другим телом обязан давать 409: " + response.body()
                        + " Иначе тест create_SameRequestIdDifferentBody_Conflicts перестанет ловить эту поломку. При смене create_SameRequestIdDifferentBody_Conflicts проверь это утверждение.");
        assertTrue(
                response.body().contains("request_id"),
                "409 обязан называть request_id: " + response.body()
                        + " Иначе тест create_SameRequestIdDifferentBody_Conflicts перестанет ловить эту поломку. При смене create_SameRequestIdDifferentBody_Conflicts проверь это утверждение.");
    }

    @Test
    void create_BotBusy_ConflictsWithActiveTaskId() throws Exception {
        String first = postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}");
        String activeId = taskId(first);
        String body =
                "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}";

        RestResponse response = send("POST", "/v1/bots/" + BOT + "/tasks", body);

        assertEquals(
                409,
                response.status(),
                "Занятый дух обязан давать 409: " + response.body()
                        + " Иначе тест create_BotBusy_ConflictsWithActiveTaskId перестанет ловить эту поломку. При смене create_BotBusy_ConflictsWithActiveTaskId проверь это утверждение.");
        assertTrue(
                response.body().contains(activeId),
                "409 обязан называть текущую задачу " + activeId + ": " + response.body()
                        + " Иначе тест create_BotBusy_ConflictsWithActiveTaskId перестанет ловить эту поломку. При смене create_BotBusy_ConflictsWithActiveTaskId проверь это утверждение.");
        assertEquals(
                1,
                registry.size(),
                "Конфликт занятости не должен резервировать вторую задачу"
                        + " Иначе тест create_BotBusy_ConflictsWithActiveTaskId перестанет ловить эту поломку. При смене create_BotBusy_ConflictsWithActiveTaskId проверь это утверждение.");
    }

    @Test
    void create_BotIdInUpperCase_ReservesUnderCanonicalLowerCaseId() throws Exception {
        String upperPath = "/v1/bots/" + BOT.toUpperCase(Locale.ROOT) + "/tasks";
        String firstBody =
                "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}";
        String secondBody =
                "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}";

        RestResponse first = send("POST", upperPath, firstBody);
        RestResponse second = send("POST", "/v1/bots/" + BOT + "/tasks", secondBody);

        assertEquals(
                202,
                first.status(),
                "Постановка по id в верхнем регистре обязана приниматься: " + first.body()
                        + " Иначе тест create_BotIdInUpperCase_ReservesUnderCanonicalLowerCaseId перестанет ловить эту поломку. При смене create_BotIdInUpperCase_ReservesUnderCanonicalLowerCaseId проверь это утверждение.");
        assertEquals(
                BOT,
                registry.get(taskId(first.body())).orElseThrow().bot(),
                "Запись обязана храниться под каноническим нижним регистром UUID: current_task в GET bots ищет по нему"
                        + " Иначе тест create_BotIdInUpperCase_ReservesUnderCanonicalLowerCaseId перестанет ловить эту поломку. При смене create_BotIdInUpperCase_ReservesUnderCanonicalLowerCaseId проверь это утверждение.");
        assertEquals(
                409,
                second.status(),
                "Тот же дух по id в нижнем регистре обязан быть занят задачей, а не получать вторую: " + second.body()
                        + " Иначе тест create_BotIdInUpperCase_ReservesUnderCanonicalLowerCaseId перестанет ловить эту поломку. При смене create_BotIdInUpperCase_ReservesUnderCanonicalLowerCaseId проверь это утверждение.");
        assertEquals(
                1,
                registry.size(),
                "Один дух — одна активная задача независимо от регистра id в пути"
                        + " Иначе тест create_BotIdInUpperCase_ReservesUnderCanonicalLowerCaseId перестанет ловить эту поломку. При смене create_BotIdInUpperCase_ReservesUnderCanonicalLowerCaseId проверь это утверждение.");
    }

    @Test
    void create_ReplayWithPlanKeysReordered_ReturnsSameTaskNotConflict() throws Exception {
        String requestId = uuid();
        String plan = "{\"type\":\"script\",\"script\":\"goto\",\"params\":{\"x\":1,\"y\":64,\"z\":2}}";
        String reorderedPlan = "{\"params\":{\"z\":2,\"y\":64,\"x\":1},\"script\":\"goto\",\"type\":\"script\"}";

        RestResponse first = send(
                "POST", "/v1/bots/" + BOT + "/tasks", "{\"request_id\":\"" + requestId + "\",\"plan\":" + plan + "}");
        RestResponse replay = send(
                "POST",
                "/v1/bots/" + BOT + "/tasks",
                "{\"plan\":" + reorderedPlan + ",\"request_id\":\"" + requestId + "\"}");

        assertEquals(
                202,
                replay.status(),
                "Повтор с пересериализованным планом (другой порядок ключей) обязан быть повтором, а не 409: "
                        + replay.body()
                        + " Иначе тест create_ReplayWithPlanKeysReordered_ReturnsSameTaskNotConflict перестанет ловить эту поломку. При смене create_ReplayWithPlanKeysReordered_ReturnsSameTaskNotConflict проверь это утверждение.");
        assertEquals(
                first.body(),
                replay.body(),
                "Повтор обязан вернуть прежний ответ дословно"
                        + " Иначе тест create_ReplayWithPlanKeysReordered_ReturnsSameTaskNotConflict перестанет ловить эту поломку. При смене create_ReplayWithPlanKeysReordered_ReturnsSameTaskNotConflict проверь это утверждение.");
        assertEquals(
                1,
                gateway.starts().size(),
                "Повтор не должен второй раз стартовать духа"
                        + " Иначе тест create_ReplayWithPlanKeysReordered_ReturnsSameTaskNotConflict перестанет ловить эту поломку. При смене create_ReplayWithPlanKeysReordered_ReturnsSameTaskNotConflict проверь это утверждение.");
    }
}
