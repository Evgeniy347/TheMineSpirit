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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

class TaskRoutesTestsPart5 {
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
    void create_AcceptedTask_IsMarkedStartedSoWatcherPollsIt() throws Exception {
        String taskId = taskId(postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}"));

        assertEquals(
                List.of(taskId),
                registry.watchableRecords().stream()
                        .map(record -> record.taskId())
                        .toList(),
                "После успешного старта у шлюза задача обязана попасть к TaskWatcher, иначе её состояние не "
                        + "обновится никогда"
                        + " При смене create_AcceptedTask_IsMarkedStartedSoWatcherPollsIt проверь это утверждение.");
    }

    @Test
    void create_GatewayStartFails_DiscardsReservation() {
        gateway.startFailsWith(new ApiException(ErrorCode.CONFLICT, "Дух занят задачей вне REST", Map.of()));
        String body =
                "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}";

        RestResponse response = send("POST", "/v1/bots/" + BOT + "/tasks", body);

        assertEquals(
                409,
                response.status(),
                "Отказ шлюза обязан проходить клиенту как 409: " + response.body()
                        + " Иначе тест create_GatewayStartFails_DiscardsReservation перестанет ловить эту поломку. При смене create_GatewayStartFails_DiscardsReservation проверь это утверждение.");
        assertEquals(
                0,
                registry.size(),
                "Провал старта обязан откатывать резерв, иначе дух навсегда занят"
                        + " При смене create_GatewayStartFails_DiscardsReservation проверь это утверждение.");
        assertEquals(
                0,
                registry.completedCount(),
                "Откат не должен оставлять хвост в реестре"
                        + " Иначе тест create_GatewayStartFails_DiscardsReservation перестанет ловить эту поломку. При смене create_GatewayStartFails_DiscardsReservation проверь это утверждение.");
    }

    @Test
    void create_UnknownBotDuringGoal_ConflictsWithoutReservation() {
        FakeTaskGateway empty = new FakeTaskGateway();
        Router fresh = Router.of(new TaskRoutes(registry, empty, planning, idempotency, slots, board).routes());
        String body =
                "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}";

        RestResponse response =
                fresh.handle(new RestRequest("POST", "/v1/bots/" + BOT + "/tasks", Map.of(), Map.of(), utf8(body)));

        assertEquals(
                404,
                response.status(),
                "Неизвестный дух обязан давать 404: " + response.body()
                        + " Иначе тест create_UnknownBotDuringGoal_ConflictsWithoutReservation перестанет ловить эту поломку. При смене create_UnknownBotDuringGoal_ConflictsWithoutReservation проверь это утверждение.");
        assertEquals(
                0,
                registry.size(),
                "404 не должен резервировать задачу"
                        + " Иначе тест create_UnknownBotDuringGoal_ConflictsWithoutReservation перестанет ловить эту поломку. При смене create_UnknownBotDuringGoal_ConflictsWithoutReservation проверь это утверждение.");
    }

    @Test
    void create_UnknownBotDuringPlan_ConflictsWithoutReservation() {
        FakeTaskGateway empty = new FakeTaskGateway()
                .startFailsWith(new ApiException(ErrorCode.NOT_FOUND, "Дух не найден", Map.of("id", BOT)));
        Router fresh = Router.of(new TaskRoutes(registry, empty, planning, idempotency, slots, board).routes());
        String body = "{\"request_id\":\"" + uuid()
                + "\",\"plan\":{\"type\":\"script\",\"script\":\"goto\",\"params\":{\"x\":1,\"y\":64,\"z\":2}}}";

        RestResponse response =
                fresh.handle(new RestRequest("POST", "/v1/bots/" + BOT + "/tasks", Map.of(), Map.of(), utf8(body)));

        assertEquals(
                404,
                response.status(),
                "Неизвестный дух обязан давать 404 и для плана: " + response.body()
                        + " Иначе тест create_UnknownBotDuringPlan_ConflictsWithoutReservation перестанет ловить эту поломку. При смене create_UnknownBotDuringPlan_ConflictsWithoutReservation проверь это утверждение.");
        assertEquals(
                0,
                registry.size(),
                "404 при плане не должен резервировать задачу"
                        + " Иначе тест create_UnknownBotDuringPlan_ConflictsWithoutReservation перестанет ловить эту поломку. При смене create_UnknownBotDuringPlan_ConflictsWithoutReservation проверь это утверждение.");
    }

    @Test
    void create_FollowWithParams_PlansAndStarts() throws Exception {
        String body = "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\","
                + "\"params\":{\"target\":\"Стейв\"}}";

        RestResponse response = send("POST", "/v1/bots/" + BOT + "/tasks", body);

        assertEquals(
                202,
                response.status(),
                "«Следуй за мной» с params.target обязан планироваться: " + response.body()
                        + " Иначе тест create_FollowWithParams_PlansAndStarts перестанет ловить эту поломку. При смене create_FollowWithParams_PlansAndStarts проверь это утверждение.");
        assertEquals(
                1,
                gateway.starts().size(),
                "План follow обязан уйти шлюзу один раз"
                        + " Иначе тест create_FollowWithParams_PlansAndStarts перестанет ловить эту поломку. При смене create_FollowWithParams_PlansAndStarts проверь это утверждение.");
    }

    @Test
    void create_FollowWithoutParams_ReturnsInvalidPlan() {
        String body = "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\"}";

        RestResponse response = send("POST", "/v1/bots/" + BOT + "/tasks", body);

        assertEquals(
                422,
                response.status(),
                "follow без params.target обязан давать 422: " + response.body()
                        + " Иначе тест create_FollowWithoutParams_ReturnsInvalidPlan перестанет ловить эту поломку. При смене create_FollowWithoutParams_ReturnsInvalidPlan проверь это утверждение.");
        assertTrue(
                response.body().contains("target"),
                "422 обязан пояснять про target: " + response.body()
                        + " Иначе тест create_FollowWithoutParams_ReturnsInvalidPlan перестанет ловить эту поломку. При смене create_FollowWithoutParams_ReturnsInvalidPlan проверь это утверждение.");
    }
}
