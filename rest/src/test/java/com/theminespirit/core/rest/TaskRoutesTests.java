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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TaskRoutesTests {
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

    @ParameterizedTest(name = "{0} {1} → {3}")
    @MethodSource("table")
    void routes_Request_ReturnsExpectedStatusAndFragment(
            String method, String section, String body, int status, String fragment) {
        String path = "tasks".equals(section) ? "/v1/bots/" + BOT + "/tasks" : "/v1/plan";

        RestResponse response = send(method, path, body);

        assertEquals(
                status,
                response.status(),
                "Статус обязан совпадать с чеклистом: " + response.body()
                        + " Иначе тест routes_Request_ReturnsExpectedStatusAndFragment перестанет ловить эту поломку. При смене routes_Request_ReturnsExpectedStatusAndFragment проверь это утверждение.");
        assertTrue(
                response.body().contains(fragment),
                "Ответ обязан содержать '" + fragment + "': " + response.body()
                        + " Иначе тест routes_Request_ReturnsExpectedStatusAndFragment перестанет ловить эту поломку. При смене routes_Request_ReturnsExpectedStatusAndFragment проверь это утверждение.");
    }

    @Test
    void create_PlanRequest_ReservesTaskAndStartsGateway() throws Exception {
        String body = "{\"request_id\":\"" + uuid()
                + "\",\"plan\":{\"type\":\"script\",\"script\":\"goto\",\"params\":{\"x\":1,\"y\":64,\"z\":2}}}";

        RestResponse response = send("POST", "/v1/bots/" + BOT + "/tasks", body);

        assertEquals(
                202,
                response.status(),
                "Валидный план обязан давать 202 Accepted: " + response.body()
                        + " Иначе тест create_PlanRequest_ReservesTaskAndStartsGateway перестанет ловить эту поломку. При смене create_PlanRequest_ReservesTaskAndStartsGateway проверь это утверждение.");
        assertEquals(
                BOT,
                gateway.starts().get(0),
                "План обязан уйти шлюзу именно для запрошенного духа"
                        + " Иначе тест create_PlanRequest_ReservesTaskAndStartsGateway перестанет ловить эту поломку. При смене create_PlanRequest_ReservesTaskAndStartsGateway проверь это утверждение.");
        assertEquals(
                1,
                registry.size(),
                "Резерв обязан создать ровно одну запись задачи"
                        + " Иначе тест create_PlanRequest_ReservesTaskAndStartsGateway перестанет ловить эту поломку. При смене create_PlanRequest_ReservesTaskAndStartsGateway проверь это утверждение.");
        assertEquals(
                TaskState.ACCEPTED,
                registry.get(taskId(response.body())).orElseThrow().state(),
                "Новая задача обязана стоять в accepted до первого опроса наблюдателя"
                        + " Иначе тест create_PlanRequest_ReservesTaskAndStartsGateway перестанет ловить эту поломку. При смене create_PlanRequest_ReservesTaskAndStartsGateway проверь это утверждение.");
    }

    @Test
    void create_AcceptedTask_RemembersGenerationReturnedByGateway() throws Exception {
        String body = "{\"request_id\":\"" + uuid()
                + "\",\"plan\":{\"type\":\"script\",\"script\":\"goto\",\"params\":{\"x\":1,\"y\":64,\"z\":2}}}";

        String taskId = taskId(send("POST", "/v1/bots/" + BOT + "/tasks", body).body());

        assertEquals(
                1,
                registry.startedGeneration(taskId),
                "Поколение задачи духа, вернувшееся из шлюза, обязано сохраниться у записи: по нему наблюдатель "
                        + "узнает вытеснение вне REST"
                        + " Иначе тест create_AcceptedTask_RemembersGenerationReturnedByGateway перестанет ловить эту поломку. При смене create_AcceptedTask_RemembersGenerationReturnedByGateway проверь это утверждение.");
    }

    @Test
    void create_GoalRequest_ReservesTaskAndStartsGateway() throws Exception {
        String body =
                "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}";

        RestResponse response = send("POST", "/v1/bots/" + BOT + "/tasks", body);

        assertEquals(
                202,
                response.status(),
                "Цель обязана планироваться и приниматься: " + response.body()
                        + " Иначе тест create_GoalRequest_ReservesTaskAndStartsGateway перестанет ловить эту поломку. При смене create_GoalRequest_ReservesTaskAndStartsGateway проверь это утверждение.");
        assertEquals(
                1,
                gateway.starts().size(),
                "После постановки цели шлюз обязан получить ровно один старт"
                        + " Иначе тест create_GoalRequest_ReservesTaskAndStartsGateway перестанет ловить эту поломку. При смене create_GoalRequest_ReservesTaskAndStartsGateway проверь это утверждение.");
    }

    @Test
    void create_ReplaySameBody_ReturnsSameTaskWithoutSecondStart() throws Exception {
        String body =
                "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}";
        String first = send("POST", "/v1/bots/" + BOT + "/tasks", body).body();

        RestResponse replay = send("POST", "/v1/bots/" + BOT + "/tasks", body);

        assertEquals(
                202,
                replay.status(),
                "Повтор того же запроса обязан оставаться 202: " + replay.body()
                        + " Иначе тест create_ReplaySameBody_ReturnsSameTaskWithoutSecondStart перестанет ловить эту поломку. При смене create_ReplaySameBody_ReturnsSameTaskWithoutSecondStart проверь это утверждение.");
        assertEquals(
                first,
                replay.body(),
                "Идемпотентный повтор обязан возвращать исходный ответ байт в байт"
                        + " Иначе тест create_ReplaySameBody_ReturnsSameTaskWithoutSecondStart перестанет ловить эту поломку. При смене create_ReplaySameBody_ReturnsSameTaskWithoutSecondStart проверь это утверждение.");
        assertEquals(
                1,
                registry.size(),
                "Идемпотентный повтор не должен создавать вторую задачу"
                        + " Иначе тест create_ReplaySameBody_ReturnsSameTaskWithoutSecondStart перестанет ловить эту поломку. При смене create_ReplaySameBody_ReturnsSameTaskWithoutSecondStart проверь это утверждение.");
        assertEquals(
                1,
                gateway.starts().size(),
                "Идемпотентный повтор не должен второй раз стартовать духа"
                        + " Иначе тест create_ReplaySameBody_ReturnsSameTaskWithoutSecondStart перестанет ловить эту поломку. При смене create_ReplaySameBody_ReturnsSameTaskWithoutSecondStart проверь это утверждение.");
    }
}
