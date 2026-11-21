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
import com.theminespirit.core.task.TaskState;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

class TaskRoutesTestsPart3 {
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

    @Test
    void create_GoalRequest_StartsGoalInSingleServerHop() throws Exception {
        String body =
                "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}";

        RestResponse response = send("POST", "/v1/bots/" + BOT + "/tasks", body);

        assertEquals(
                202,
                response.status(),
                "Цель обязана приниматься: " + response.body()
                        + " Иначе тест create_GoalRequest_StartsGoalInSingleServerHop перестанет ловить эту поломку. При смене create_GoalRequest_StartsGoalInSingleServerHop проверь это утверждение.");
        assertEquals(
                1,
                gateway.hops(),
                "Наблюдение, планирование FallbackPlanner и старт цели обязаны идти одним переходом на серверный "
                        + "поток (п. 13г), а не двумя: " + response.body()
                        + " Иначе тест create_GoalRequest_StartsGoalInSingleServerHop перестанет ловить эту поломку. При смене create_GoalRequest_StartsGoalInSingleServerHop проверь это утверждение.");
    }

    @Test
    void create_PlanRequest_StartsPlanInSingleServerHop() throws Exception {
        String body = "{\"request_id\":\"" + uuid()
                + "\",\"plan\":{\"type\":\"script\",\"script\":\"goto\",\"params\":{\"x\":1,\"y\":64,\"z\":2}}}";

        RestResponse response = send("POST", "/v1/bots/" + BOT + "/tasks", body);

        assertEquals(
                202,
                response.status(),
                "План обязана приниматься: " + response.body()
                        + " Иначе тест create_PlanRequest_StartsPlanInSingleServerHop перестанет ловить эту поломку. При смене create_PlanRequest_StartsPlanInSingleServerHop проверь это утверждение.");
        assertEquals(
                1,
                gateway.hops(),
                "Старт присланного плана — один переход на серверный поток: " + response.body()
                        + " Иначе тест create_PlanRequest_StartsPlanInSingleServerHop перестанет ловить эту поломку. При смене create_PlanRequest_StartsPlanInSingleServerHop проверь это утверждение.");
    }

    @Test
    void create_BotBusy_ConflictsBeforeAnyServerHop() throws Exception {
        postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}");
        int hopsBefore = gateway.hops();
        String body =
                "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}";

        RestResponse response = send("POST", "/v1/bots/" + BOT + "/tasks", body);

        assertEquals(
                409,
                response.status(),
                "Занятый дух обязан давать 409: " + response.body()
                        + " Иначе тест create_BotBusy_ConflictsBeforeAnyServerHop перестанет ловить эту поломку. При смене create_BotBusy_ConflictsBeforeAnyServerHop проверь это утверждение.");
        assertEquals(
                hopsBefore,
                gateway.hops(),
                "409 по брони реестра обязан приходить до любого перехода на серверный поток, а не после "
                        + "наблюдения: " + response.body()
                        + " Иначе тест create_BotBusy_ConflictsBeforeAnyServerHop перестанет ловить эту поломку. При смене create_BotBusy_ConflictsBeforeAnyServerHop проверь это утверждение.");
    }

    @Test
    void create_ReplayWhileBotBusyWithSameTask_Returns202WithSameTaskIdNotConflict() throws Exception {
        String body =
                "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}";
        String first = send("POST", "/v1/bots/" + BOT + "/tasks", body).body();
        String taskId = taskId(first);
        registry.observe(taskId, TaskState.RUNNING, null, null);
        gateway.startFailsWith(new ApiException(ErrorCode.CONFLICT, "Дух занят задачей вне REST", Map.of()));

        RestResponse replay = send("POST", "/v1/bots/" + BOT + "/tasks", body);

        assertEquals(
                202,
                replay.status(),
                "Повтор запроса к духу, занятому этой же задачей, обязан вернуть 202, а не 409 «дух занят»: проверка "
                        + "request_id идёт раньше проверки занятости (R15). Ответ: " + replay.body()
                        + " Иначе тест create_ReplayWhileBotBusyWithSameTask_Returns202WithSameTaskIdNotConflict перестанет ловить эту поломку. При смене create_ReplayWhileBotBusyWithSameTask_Returns202WithSameTaskIdNotConflict проверь это утверждение.");
        assertEquals(
                taskId,
                taskId(replay.body()),
                "Повтор обязан вернуть ту же задачу " + taskId + ": " + replay.body()
                        + " Иначе тест create_ReplayWhileBotBusyWithSameTask_Returns202WithSameTaskIdNotConflict перестанет ловить эту поломку. При смене create_ReplayWhileBotBusyWithSameTask_Returns202WithSameTaskIdNotConflict проверь это утверждение.");
        assertEquals(
                1,
                gateway.starts().size(),
                "Повтор не должен ещё раз обращаться к духу через шлюз"
                        + " Иначе тест create_ReplayWhileBotBusyWithSameTask_Returns202WithSameTaskIdNotConflict перестанет ловить эту поломку. При смене create_ReplayWhileBotBusyWithSameTask_Returns202WithSameTaskIdNotConflict проверь это утверждение.");
        assertEquals(
                1,
                registry.size(),
                "Повтор не должен резервировать вторую задачу"
                        + " Иначе тест create_ReplayWhileBotBusyWithSameTask_Returns202WithSameTaskIdNotConflict перестанет ловить эту поломку. При смене create_ReplayWhileBotBusyWithSameTask_Returns202WithSameTaskIdNotConflict проверь это утверждение.");
        RestResponse fresh = send(
                "POST",
                "/v1/bots/" + BOT + "/tasks",
                "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}");
        assertEquals(
                409,
                fresh.status(),
                "Контроль: новый request_id к тому же занятому духу обязан давать 409, иначе тест не видит занятости: "
                        + fresh.body()
                        + " При смене create_ReplayWhileBotBusyWithSameTask_Returns202WithSameTaskIdNotConflict проверь это утверждение.");
        assertTrue(
                fresh.body().contains(taskId),
                "409 обязан называть текущую задачу " + taskId + ": " + fresh.body()
                        + " Иначе тест create_ReplayWhileBotBusyWithSameTask_Returns202WithSameTaskIdNotConflict перестанет ловить эту поломку. При смене create_ReplayWhileBotBusyWithSameTask_Returns202WithSameTaskIdNotConflict проверь это утверждение.");
    }
}
