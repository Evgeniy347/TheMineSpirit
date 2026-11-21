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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

class TaskRoutesTestsPart8 {
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
    void events_WatcherFinishesTaskWhileRequestWaitsForSlot_DeliversFinalState() throws Exception {
        String taskId = taskId(postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}"));
        registry.observe(taskId, TaskState.RUNNING, null, null);
        CountDownLatch slotsLocked = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        SseSlots lockedSlots = slots;
        Thread locker = new Thread(() -> {
            synchronized (lockedSlots) {
                slotsLocked.countDown();
                try {
                    proceed.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        locker.setDaemon(true);
        locker.start();
        Threads.await(slotsLocked, "Тест обязан удерживать монитор слотов до старта HTTP-запроса");

        AtomicReference<RestResponse> opened = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread http = new Thread(() -> {
            try {
                opened.set(router.handle(
                        new RestRequest("GET", "/v1/tasks/" + taskId + "/events", Map.of(), Map.of(), utf8(""))));
            } catch (Throwable broken) {
                failure.set(broken);
            }
        });
        http.setDaemon(true);
        http.start();
        ThreadStates.awaitState(
                http,
                Thread.State.BLOCKED,
                "HTTP-поток обязан ждать слот: запись задачи уже прочитана, позиция журнала ещё нет");

        registry.observe(taskId, TaskState.SUCCEEDED, null, null);
        proceed.countDown();
        Threads.join(http, "HTTP-запрос обязан завершиться после освобождения слотов");
        Threads.join(locker, "Поток, державший слоты, обязан завершиться");
        assertNull(
                failure.get(),
                "HTTP-запрос обязан вернуть ответ, а не упасть исключением: " + failure.get()
                        + " Иначе тест events_WatcherFinishesTaskWhileRequestWaitsForSlot_DeliversFinalState перестанет ловить эту поломку. При смене events_WatcherFinishesTaskWhileRequestWaitsForSlot_DeliversFinalState проверь это утверждение.");
        assertEquals(
                200,
                opened.get().status(),
                "HTTP-запрос обязан открывать поток событий: " + opened.get().body()
                        + " Иначе тест events_WatcherFinishesTaskWhileRequestWaitsForSlot_DeliversFinalState перестанет ловить эту поломку. При смене events_WatcherFinishesTaskWhileRequestWaitsForSlot_DeliversFinalState проверь это утверждение.");

        List<String> frames = new ArrayList<>();
        try (SseStream stream = opened.get().stream()) {
            String frame;
            while ((frame = stream.nextFrame()) != null) {
                frames.add(frame);
            }
        }
        assertTrue(
                frames.stream().anyMatch(frame -> frame.contains("\"state\":\"succeeded\"")),
                "Пока HTTP-поток ждал слоты, watcher мог закрыть задачу: финальное состояние обязано дойти "
                        + "до клиента, а не потеряться за уже финальной позицией журнала: " + frames
                        + " Иначе тест events_WatcherFinishesTaskWhileRequestWaitsForSlot_DeliversFinalState перестанет ловить эту поломку. При смене events_WatcherFinishesTaskWhileRequestWaitsForSlot_DeliversFinalState проверь это утверждение.");
    }

    @Test
    void events_FifthStream_ReturnsRateLimited() throws Exception {
        String taskId = taskId(postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}"));
        List<RestResponse> open = new ArrayList<>();
        for (int i = 0; i < SseSlots.MAX_STREAMS; i++) {
            RestResponse response = send("GET", "/v1/tasks/" + taskId + "/events", "");
            assertEquals(
                    200,
                    response.status(),
                    "Четыре потока обязаны открываться: " + response.body()
                            + " Иначе тест events_FifthStream_ReturnsRateLimited перестанет ловить эту поломку. При смене events_FifthStream_ReturnsRateLimited проверь это утверждение.");
            open.add(response);
        }

        RestResponse fifth = send("GET", "/v1/tasks/" + taskId + "/events", "");

        assertEquals(
                429,
                fifth.status(),
                "Пятый поток обязан получать 429: " + fifth.body()
                        + " Иначе тест events_FifthStream_ReturnsRateLimited перестанет ловить эту поломку. При смене events_FifthStream_ReturnsRateLimited проверь это утверждение.");
        assertTrue(
                fifth.body().contains("rate_limited"),
                "429 обязан нести code rate_limited: " + fifth.body()
                        + " Иначе тест events_FifthStream_ReturnsRateLimited перестанет ловить эту поломку. При смене events_FifthStream_ReturnsRateLimited проверь это утверждение.");
        open.forEach(response -> response.stream().close());
        assertEquals(
                0,
                slots.open(),
                "Все закрытые потоки обязаны освободить слоты"
                        + " Иначе тест events_FifthStream_ReturnsRateLimited перестанет ловить эту поломку. При смене events_FifthStream_ReturnsRateLimited проверь это утверждение.");
        RestResponse afterRelease = send("GET", "/v1/tasks/" + taskId + "/events", "");
        assertEquals(
                200,
                afterRelease.status(),
                "После освобождения слота поток обязан открываться снова: " + afterRelease.body()
                        + " Иначе тест events_FifthStream_ReturnsRateLimited перестанет ловить эту поломку. При смене events_FifthStream_ReturnsRateLimited проверь это утверждение.");
        afterRelease.stream().close();
    }
}
