package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

class TaskRoutesTestsPart9 {
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
    void events_InvalidLastEventIdHeader_ReturnsBadRequest() throws Exception {
        String taskId = taskId(postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}"));

        RestResponse response = send("GET", "/v1/tasks/" + taskId + "/events", "", Map.of("Last-Event-ID", "abc"));

        assertEquals(
                400,
                response.status(),
                "Нечисловой Last-Event-ID обязан давать 400: " + response.body()
                        + " Иначе тест events_InvalidLastEventIdHeader_ReturnsBadRequest перестанет ловить эту поломку. При смене events_InvalidLastEventIdHeader_ReturnsBadRequest проверь это утверждение.");
        assertNull(
                response.stream(),
                "400 не должен открывать поток кадров"
                        + " Иначе тест events_InvalidLastEventIdHeader_ReturnsBadRequest перестанет ловить эту поломку. При смене events_InvalidLastEventIdHeader_ReturnsBadRequest проверь это утверждение.");
        assertEquals(
                0,
                slots.open(),
                "Открытый и отклонённый поток не должен держать слот"
                        + " Иначе тест events_InvalidLastEventIdHeader_ReturnsBadRequest перестанет ловить эту поломку. При смене events_InvalidLastEventIdHeader_ReturnsBadRequest проверь это утверждение.");
    }

    @Test
    void events_InvalidLastEventId_DoesNotLeakSlot() throws Exception {
        String taskId = taskId(postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}"));
        RestResponse ok = send("GET", "/v1/tasks/" + taskId + "/events", "");
        assertEquals(
                200,
                ok.status(),
                "Первый поток обязан открываться: " + ok.body()
                        + " Иначе тест events_InvalidLastEventId_DoesNotLeakSlot перестанет ловить эту поломку. При смене events_InvalidLastEventId_DoesNotLeakSlot проверь это утверждение.");

        RestResponse bad = send("GET", "/v1/tasks/" + taskId + "/events", "", Map.of("last-event-id", "не-число"));

        assertEquals(
                400,
                bad.status(),
                "Нечисловой заголовок обязан давать 400: " + bad.body()
                        + " Иначе тест events_InvalidLastEventId_DoesNotLeakSlot перестанет ловить эту поломку. При смене events_InvalidLastEventId_DoesNotLeakSlot проверь это утверждение.");
        assertEquals(
                1,
                slots.open(),
                "Отклонённый запрос не должен занимать слот: открыт только первый поток"
                        + " Иначе тест events_InvalidLastEventId_DoesNotLeakSlot перестанет ловить эту поломку. При смене events_InvalidLastEventId_DoesNotLeakSlot проверь это утверждение.");
        ok.stream().close();
    }

    @Test
    void events_StreamingStateAndCancellation_FrameFlow() throws Exception {
        String taskId = taskId(postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}"));
        RestResponse response = send("GET", "/v1/tasks/" + taskId + "/events", "");
        try (SseStream stream = response.stream()) {
            String first = stream.nextFrame();
            assertTrue(
                    first.contains("event: state"),
                    "Первый кадр обязана быть state: " + first
                            + " Иначе тест events_StreamingStateAndCancellation_FrameFlow перестанет ловить эту поломку. При смене events_StreamingStateAndCancellation_FrameFlow проверь это утверждение.");

            registry.observe(taskId, TaskState.RUNNING, null, null);

            send("DELETE", "/v1/tasks/" + taskId, "");

            String running = stream.nextFrame();
            assertTrue(
                    running.contains("event: state") && running.contains("running"),
                    "Первым событием обязана идти смена состояния на running: " + running
                            + " Иначе тест events_StreamingStateAndCancellation_FrameFlow перестанет ловить эту поломку. При смене events_StreamingStateAndCancellation_FrameFlow проверь это утверждение.");

            String cancelled = stream.nextFrame();
            assertTrue(
                    cancelled.contains("event: state") && cancelled.contains("cancelled"),
                    "После отмены клиент обязан получить кадр state cancelled: " + cancelled
                            + " Иначе тест events_StreamingStateAndCancellation_FrameFlow перестанет ловить эту поломку. При смене events_StreamingStateAndCancellation_FrameFlow проверь это утверждение.");

            assertNull(
                    stream.nextFrame(),
                    "Закрытый журнал обязан завершать поток значением null"
                            + " Иначе тест events_StreamingStateAndCancellation_FrameFlow перестанет ловить эту поломку. При смене events_StreamingStateAndCancellation_FrameFlow проверь это утверждение.");
        }
    }

    @Test
    void plan_ValidNode_ReturnsValidTrue() {
        RestResponse response = send(
                "POST",
                "/v1/plan",
                "{\"plan\":{\"type\":\"script\",\"script\":\"goto\",\"params\":{\"x\":1,\"y\":64,\"z\":2}}}");

        assertEquals(
                200,
                response.status(),
                "Валидный план обязана приниматься сухим прогоном: " + response.body()
                        + " Иначе тест plan_ValidNode_ReturnsValidTrue перестанет ловить эту поломку. При смене plan_ValidNode_ReturnsValidTrue проверь это утверждение.");
        assertEquals(
                "{\"valid\":true}",
                response.body(),
                "Сухой прогон обязан отвечать ровно {\"valid\":true}"
                        + " Иначе тест plan_ValidNode_ReturnsValidTrue перестанет ловить эту поломку. При смене plan_ValidNode_ReturnsValidTrue проверь это утверждение.");
        assertEquals(
                0,
                registry.size(),
                "Сухой прогон не должен трогать реестр задач"
                        + " Иначе тест plan_ValidNode_ReturnsValidTrue перестанет ловить эту поломку. При смене plan_ValidNode_ReturnsValidTrue проверь это утверждение.");
        assertTrue(
                gateway.starts().isEmpty(),
                "Сухой прогон не должен стартовать духа"
                        + " Иначе тест plan_ValidNode_ReturnsValidTrue перестанет ловить эту поломку. При смене plan_ValidNode_ReturnsValidTrue проверь это утверждение.");
    }
}
