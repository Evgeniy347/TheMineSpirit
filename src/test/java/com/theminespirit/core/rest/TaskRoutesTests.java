package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.ScriptCatalog;
import com.theminespirit.core.task.TaskRegistry;
import com.theminespirit.core.task.TaskState;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
    private IdempotencyCache idempotency;
    private SseSlots slots;
    private Router router;

    @BeforeEach
    void setUp() {
        registry = new TaskRegistry();
        planning = new PlanningService(CATALOG, ConditionCatalog.empty());
        gateway = new FakeTaskGateway().observing(OBSERVATION).planning(planning);
        idempotency = new IdempotencyCache();
        slots = new SseSlots();
        router = Router.of(new TaskRoutes(registry, gateway, planning, idempotency, slots).routes());
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
        assertEquals(202, response.status(), "Постановка задачи обязана приниматься: тело " + response.body());
        return response.body();
    }

    private String taskId(String acceptedBody) throws Exception {
        return MAPPER.readTree(acceptedBody).get("task_id").asText();
    }

    private String runToState(String taskId, TaskState state, String error) {
        return registry.observe(taskId, state, null, error).toString();
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

        assertEquals(status, response.status(), "Статус обязан совпадать с чеклистом: " + response.body());
        assertTrue(response.body().contains(fragment), "Ответ обязан содержать '" + fragment + "': " + response.body());
    }

    @Test
    void create_PlanRequest_ReservesTaskAndStartsGateway() throws Exception {
        String body = "{\"request_id\":\"" + uuid()
                + "\",\"plan\":{\"type\":\"script\",\"script\":\"goto\",\"params\":{\"x\":1,\"y\":64,\"z\":2}}}";

        RestResponse response = send("POST", "/v1/bots/" + BOT + "/tasks", body);

        assertEquals(202, response.status(), "Валидный план обязан давать 202 Accepted: " + response.body());
        assertEquals(BOT, gateway.starts().get(0), "План обязан уйти шлюзу именно для запрошенного духа");
        assertEquals(1, registry.size(), "Резерв обязан создать ровно одну запись задачи");
        assertEquals(
                TaskState.ACCEPTED,
                registry.get(taskId(response.body())).orElseThrow().state(),
                "Новая задача обязана стоять в accepted до первого опроса наблюдателя");
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
                        + "узнает вытеснение вне REST");
    }

    @Test
    void create_GoalRequest_ReservesTaskAndStartsGateway() throws Exception {
        String body =
                "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}";

        RestResponse response = send("POST", "/v1/bots/" + BOT + "/tasks", body);

        assertEquals(202, response.status(), "Цель обязана планироваться и приниматься: " + response.body());
        assertEquals(1, gateway.starts().size(), "После постановки цели шлюз обязан получить ровно один старт");
    }

    @Test
    void create_ReplaySameBody_ReturnsSameTaskWithoutSecondStart() throws Exception {
        String body =
                "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}";
        String first = send("POST", "/v1/bots/" + BOT + "/tasks", body).body();

        RestResponse replay = send("POST", "/v1/bots/" + BOT + "/tasks", body);

        assertEquals(202, replay.status(), "Повтор того же запроса обязан оставаться 202: " + replay.body());
        assertEquals(first, replay.body(), "Идемпотентный повтор обязан возвращать исходный ответ байт в байт");
        assertEquals(1, registry.size(), "Идемпотентный повтор не должен создавать вторую задачу");
        assertEquals(1, gateway.starts().size(), "Идемпотентный повтор не должен второй раз стартовать духа");
    }

    @Test
    void create_SameRequestIdDifferentBody_Conflicts() {
        String first =
                "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}";
        send("POST", "/v1/bots/" + BOT + "/tasks", first);
        String other = "{\"request_id\":\"" + jsonField(first, "request_id")
                + "\",\"plan\":{\"type\":\"script\",\"script\":\"goto\",\"params\":{\"x\":1,\"y\":64,\"z\":2}}}";

        RestResponse response = send("POST", "/v1/bots/" + BOT + "/tasks", other);

        assertEquals(409, response.status(), "Тот же request_id с другим телом обязан давать 409: " + response.body());
        assertTrue(response.body().contains("request_id"), "409 обязан называть request_id: " + response.body());
    }

    @Test
    void create_BotBusy_ConflictsWithActiveTaskId() throws Exception {
        String first = postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}");
        String activeId = taskId(first);
        String body =
                "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}";

        RestResponse response = send("POST", "/v1/bots/" + BOT + "/tasks", body);

        assertEquals(409, response.status(), "Занятый дух обязан давать 409: " + response.body());
        assertTrue(
                response.body().contains(activeId),
                "409 обязан называть текущую задачу " + activeId + ": " + response.body());
        assertEquals(1, registry.size(), "Конфликт занятости не должен резервировать вторую задачу");
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

        assertEquals(202, first.status(), "Постановка по id в верхнем регистре обязана приниматься: " + first.body());
        assertEquals(
                BOT,
                registry.get(taskId(first.body())).orElseThrow().bot(),
                "Запись обязана храниться под каноническим нижним регистром UUID: current_task в GET bots ищет по нему");
        assertEquals(
                409,
                second.status(),
                "Тот же дух по id в нижнем регистре обязан быть занят задачей, а не получать вторую: " + second.body());
        assertEquals(1, registry.size(), "Один дух — одна активная задача независимо от регистра id в пути");
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
                        + replay.body());
        assertEquals(first.body(), replay.body(), "Повтор обязан вернуть прежний ответ дословно");
        assertEquals(1, gateway.starts().size(), "Повтор не должен второй раз стартовать духа");
    }

    @Test
    void create_GoalRequest_StartsGoalInSingleServerHop() throws Exception {
        String body =
                "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}";

        RestResponse response = send("POST", "/v1/bots/" + BOT + "/tasks", body);

        assertEquals(202, response.status(), "Цель обязана приниматься: " + response.body());
        assertEquals(
                1,
                gateway.hops(),
                "Наблюдение, планирование FallbackPlanner и старт цели обязаны идти одним переходом на серверный "
                        + "поток (п. 13г), а не двумя: " + response.body());
    }

    @Test
    void create_PlanRequest_StartsPlanInSingleServerHop() throws Exception {
        String body = "{\"request_id\":\"" + uuid()
                + "\",\"plan\":{\"type\":\"script\",\"script\":\"goto\",\"params\":{\"x\":1,\"y\":64,\"z\":2}}}";

        RestResponse response = send("POST", "/v1/bots/" + BOT + "/tasks", body);

        assertEquals(202, response.status(), "План обязана приниматься: " + response.body());
        assertEquals(
                1, gateway.hops(), "Старт присланного плана — один переход на серверный поток: " + response.body());
    }

    @Test
    void create_BotBusy_ConflictsBeforeAnyServerHop() throws Exception {
        postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}");
        int hopsBefore = gateway.hops();
        String body =
                "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}";

        RestResponse response = send("POST", "/v1/bots/" + BOT + "/tasks", body);

        assertEquals(409, response.status(), "Занятый дух обязан давать 409: " + response.body());
        assertEquals(
                hopsBefore,
                gateway.hops(),
                "409 по брони реестра обязан приходить до любого перехода на серверный поток, а не после "
                        + "наблюдения: " + response.body());
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
                        + "request_id идёт раньше проверки занятости (R15). Ответ: " + replay.body());
        assertEquals(
                taskId, taskId(replay.body()), "Повтор обязан вернуть ту же задачу " + taskId + ": " + replay.body());
        assertEquals(1, gateway.starts().size(), "Повтор не должен ещё раз обращаться к духу через шлюз");
        assertEquals(1, registry.size(), "Повтор не должен резервировать вторую задачу");
        RestResponse fresh = send(
                "POST",
                "/v1/bots/" + BOT + "/tasks",
                "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}");
        assertEquals(
                409,
                fresh.status(),
                "Контроль: новый request_id к тому же занятому духу обязан давать 409, иначе тест не видит занятости: "
                        + fresh.body());
        assertTrue(fresh.body().contains(taskId), "409 обязан называть текущую задачу " + taskId + ": " + fresh.body());
    }

    @Test
    void create_SecondIdenticalRequestWhileFirstInFlight_ReplaysInsteadOfConflict() throws Exception {
        CountDownLatch firstInStart = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        gateway.runsOnStart(() -> {
            firstInStart.countDown();
            await(releaseFirst);
        });
        String body =
                "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}";
        AtomicReference<RestResponse> firstOpened = new AtomicReference<>();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        Thread first = postInBackground(body, firstOpened, firstFailure);
        first.start();
        assertTrue(firstInStart.await(5, TimeUnit.SECONDS), "Первый запрос обязан дойти до шлюза до старта дубликата");

        AtomicReference<RestResponse> secondOpened = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();
        Thread second = postInBackground(body, secondOpened, secondFailure);
        second.start();
        ThreadStates.awaitState(
                second,
                Thread.State.WAITING,
                "Дубликат с тем же request_id обязан ждать результат первого, а не получить 409 «дух занят»");

        releaseFirst.countDown();
        first.join(TimeUnit.SECONDS.toMillis(5));
        second.join(TimeUnit.SECONDS.toMillis(5));

        assertNull(firstFailure.get(), "Первый запрос обязан завершиться ответом: " + firstFailure.get());
        assertNull(secondFailure.get(), "Дубликат обязан завершиться ответом: " + secondFailure.get());
        assertEquals(
                202,
                firstOpened.get().status(),
                "Первый запрос обязан приняться: " + firstOpened.get().body());
        assertEquals(
                202,
                secondOpened.get().status(),
                "Дубликат обязан получить тот же ответ, а не 409: "
                        + secondOpened.get().body());
        assertEquals(
                firstOpened.get().body(),
                secondOpened.get().body(),
                "Идемпотентный повтор обязан отдать исходный ответ дословно");
        assertEquals(1, registry.size(), "Дубликат не должен резервировать вторую задачу");
    }

    @Test
    void create_FirstRequestFailsWhileDuplicateWaits_SecondGoesItsOwnWay() throws Exception {
        CountDownLatch firstInStart = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger starts = new AtomicInteger();
        gateway.runsOnStart(() -> {
            int attempt = starts.incrementAndGet();
            firstInStart.countDown();
            await(releaseFirst);
            if (attempt == 1) {
                throw new ApiException(ErrorCode.CONFLICT, "Дух занят задачей вне REST", Map.of());
            }
        });
        String body =
                "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}";
        AtomicReference<RestResponse> firstOpened = new AtomicReference<>();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        Thread first = postInBackground(body, firstOpened, firstFailure);
        first.start();
        assertTrue(firstInStart.await(5, TimeUnit.SECONDS), "Первый запрос обязан дойти до шлюза до старта дубликата");

        AtomicReference<RestResponse> secondOpened = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();
        Thread second = postInBackground(body, secondOpened, secondFailure);
        second.start();
        ThreadStates.awaitState(
                second, Thread.State.WAITING, "Дубликат обязан ждать результат первого, пока тот не провалится");

        releaseFirst.countDown();
        first.join(TimeUnit.SECONDS.toMillis(5));
        second.join(TimeUnit.SECONDS.toMillis(5));

        assertNull(firstFailure.get(), "Первый запрос обязан завершиться ответом: " + firstFailure.get());
        assertNull(secondFailure.get(), "Дубликат обязан завершиться ответом: " + secondFailure.get());
        assertEquals(
                409,
                firstOpened.get().status(),
                "Провал шлюза обязан пройти клиенту как 409: "
                        + firstOpened.get().body());
        assertEquals(
                202,
                secondOpened.get().status(),
                "После снятия брони дубликат обязан идти своим ходом, а не ждать вечно: "
                        + secondOpened.get().body());
        assertEquals(1, registry.size(), "За дубликатом должна остаться одна задача");
    }

    private Thread postInBackground(
            String body, AtomicReference<RestResponse> opened, AtomicReference<Throwable> failure) {
        Thread worker = new Thread(() -> {
            try {
                opened.set(send("POST", "/v1/bots/" + BOT + "/tasks", body));
            } catch (Throwable broken) {
                failure.set(broken);
            }
        });
        worker.setDaemon(true);
        return worker;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
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
                        + "обновится никогда");
    }

    @Test
    void create_GatewayStartFails_DiscardsReservation() {
        gateway.startFailsWith(new ApiException(ErrorCode.CONFLICT, "Дух занят задачей вне REST", Map.of()));
        String body =
                "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}";

        RestResponse response = send("POST", "/v1/bots/" + BOT + "/tasks", body);

        assertEquals(409, response.status(), "Отказ шлюза обязан проходить клиенту как 409: " + response.body());
        assertEquals(0, registry.size(), "Провал старта обязан откатывать резерв, иначе дух навсегда занят");
        assertEquals(0, registry.completedCount(), "Откат не должен оставлять хвост в реестре");
    }

    @Test
    void create_UnknownBotDuringGoal_ConflictsWithoutReservation() {
        FakeTaskGateway empty = new FakeTaskGateway();
        Router fresh = Router.of(new TaskRoutes(registry, empty, planning, idempotency, slots).routes());
        String body =
                "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}";

        RestResponse response =
                fresh.handle(new RestRequest("POST", "/v1/bots/" + BOT + "/tasks", Map.of(), Map.of(), utf8(body)));

        assertEquals(404, response.status(), "Неизвестный дух обязан давать 404: " + response.body());
        assertEquals(0, registry.size(), "404 не должен резервировать задачу");
    }

    @Test
    void create_UnknownBotDuringPlan_ConflictsWithoutReservation() {
        FakeTaskGateway empty = new FakeTaskGateway()
                .startFailsWith(new ApiException(ErrorCode.NOT_FOUND, "Дух не найден", Map.of("id", BOT)));
        Router fresh = Router.of(new TaskRoutes(registry, empty, planning, idempotency, slots).routes());
        String body = "{\"request_id\":\"" + uuid()
                + "\",\"plan\":{\"type\":\"script\",\"script\":\"goto\",\"params\":{\"x\":1,\"y\":64,\"z\":2}}}";

        RestResponse response =
                fresh.handle(new RestRequest("POST", "/v1/bots/" + BOT + "/tasks", Map.of(), Map.of(), utf8(body)));

        assertEquals(404, response.status(), "Неизвестный дух обязан давать 404 и для плана: " + response.body());
        assertEquals(0, registry.size(), "404 при плане не должен резервировать задачу");
    }

    @Test
    void create_FollowWithParams_PlansAndStarts() throws Exception {
        String body = "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\","
                + "\"params\":{\"target\":\"Стейв\"}}";

        RestResponse response = send("POST", "/v1/bots/" + BOT + "/tasks", body);

        assertEquals(
                202, response.status(), "«Следуй за мной» с params.target обязан планироваться: " + response.body());
        assertEquals(1, gateway.starts().size(), "План follow обязан уйти шлюзу один раз");
    }

    @Test
    void create_FollowWithoutParams_ReturnsInvalidPlan() {
        String body = "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\"}";

        RestResponse response = send("POST", "/v1/bots/" + BOT + "/tasks", body);

        assertEquals(422, response.status(), "follow без params.target обязан давать 422: " + response.body());
        assertTrue(response.body().contains("target"), "422 обязан пояснять про target: " + response.body());
    }

    @Test
    void get_KnownTask_ReturnsStateAndBot() throws Exception {
        String taskId = taskId(postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}"));

        RestResponse response = send("GET", "/v1/tasks/" + taskId, "");

        assertEquals(200, response.status(), "Известная задача обязана читаться: " + response.body());
        assertTrue(
                response.body().contains("\"state\":\"accepted\""),
                "GET обязан отдавать состояние задачи: " + response.body());
        assertTrue(response.body().contains(BOT), "GET обязан отдавать духа: " + response.body());
    }

    @Test
    void get_UnknownTask_ReturnsNotFound() {
        RestResponse response = send("GET", "/v1/tasks/t-404", "");

        assertEquals(404, response.status(), "Неизвестная задача обязана давать 404: " + response.body());
        assertTrue(response.body().contains("t-404"), "404 обязан называть task_id: " + response.body());
        assertTrue(
                response.body().contains("сервер перезапущен")
                        && response.body().contains("задача утрачена"),
                "404 после перезапуска обязан объяснять потерю, а не молчать: " + response.body());
    }

    @Test
    void delete_ActiveTask_CancelsViaGatewayAndRegistry() throws Exception {
        String taskId = taskId(postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}"));

        RestResponse response = send("DELETE", "/v1/tasks/" + taskId, "");

        assertEquals(200, response.status(), "Отмена обязана отвечать 200: " + response.body());
        assertTrue(
                response.body().contains("\"state\":\"cancelled\""),
                "200 обязан возвращать cancelled: " + response.body());
        assertEquals(List.of(taskId), gateway.cancels(), "Активная задача обязана останавливать дух через шлюз");
        assertEquals(
                TaskState.CANCELLED, registry.get(taskId).orElseThrow().state(), "Реестр обязан перейти в cancelled");
    }

    @Test
    void delete_Twice_ReturnsCancelledWithoutSecondCancel() throws Exception {
        String taskId = taskId(postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}"));

        RestResponse first = send("DELETE", "/v1/tasks/" + taskId, "");
        RestResponse second = send("DELETE", "/v1/tasks/" + taskId, "");

        assertEquals(200, second.status(), "Повторный DELETE обязан оставаться идемпотентным 200: " + second.body());
        assertEquals(first.body(), second.body(), "Повторный DELETE обязан возвращать ту же запись задачи");
        assertEquals(
                List.of(taskId), gateway.cancels(), "Идемпотентный повтор DELETE не должен второй раз трогать духа");
    }

    @Test
    void delete_GatewayFinishesTaskDuringCancel_ReturnsCurrentRecordWithoutIllegalTransition() throws Exception {
        String taskId = taskId(postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}"));
        gateway.runsOnCancel(() -> registry.observe(taskId, TaskState.SUCCEEDED, null, null));

        RestResponse response = send("DELETE", "/v1/tasks/" + taskId, "");

        assertEquals(
                200,
                response.status(),
                "Между чтением записи и отменой watcher мог успеть закрыть задачу: DELETE обязан вернуть текущую "
                        + "запись, а не упасть недопустимым переходом cancelled, который сервер отдаёт как 500: "
                        + response.body());
        assertTrue(
                response.body().contains("\"state\":\"succeeded\""),
                "Ответ обязан нести текущее состояние задачи, а не отменённую копию: " + response.body());
    }

    @Test
    void delete_UnknownTask_ReturnsNotFound() {
        RestResponse response = send("DELETE", "/v1/tasks/t-404", "");

        assertEquals(404, response.status(), "Отмена неизвестной задачи обязана давать 404: " + response.body());
        assertTrue(gateway.cancels().isEmpty(), "404 не должен вызывать cancel у шлюза");
    }

    @Test
    void delete_TerminalTask_ReturnsRecordWithoutCancel() throws Exception {
        String taskId = taskId(postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}"));
        runToState(taskId, TaskState.RUNNING, null);
        runToState(taskId, TaskState.SUCCEEDED, null);

        RestResponse response = send("DELETE", "/v1/tasks/" + taskId, "");

        assertEquals(
                200, response.status(), "DELETE завершённой задачи обязан быть идемпотентным 200: " + response.body());
        assertTrue(
                response.body().contains("\"state\":\"succeeded\""),
                "Завершённая задача обязана вернуть своё состояние: " + response.body());
        assertTrue(gateway.cancels().isEmpty(), "Завершённой задаче отмена не нужна");
    }

    @Test
    void events_UnknownTask_ReturnsNotFound() {
        RestResponse response = send("GET", "/v1/tasks/t-404/events", "");

        assertEquals(404, response.status(), "SSE у неизвестной задачи обязан давать 404: " + response.body());
        assertNull(response.stream(), "404 не должен открывать поток кадров");
        assertTrue(
                response.body().contains("сервер перезапущен")
                        && response.body().contains("задача утрачена"),
                "SSE после перезапуска обязан объяснять потерю, а не молчать: " + response.body());
    }

    @Test
    void events_KnownTask_OpensStreamWithStateFrame() throws Exception {
        String taskId = taskId(postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}"));

        RestResponse response = send("GET", "/v1/tasks/" + taskId + "/events", "");

        assertEquals(200, response.status(), "SSE обязана открываться 200: " + response.body());
        assertTrue(response.stream() != null, "Ответ обязан нести поток кадров");
        assertEquals(
                "text/event-stream",
                response.headers().get("Content-Type"),
                "SSE обязана отдаваться с content-type text/event-stream");
        try (SseStream stream = response.stream()) {
            String frame = stream.nextFrame();
            assertTrue(frame.startsWith("id: "), "Первый кадр обязан нести id: " + frame);
            assertTrue(frame.contains("event: state"), "Первый кадр обязан быть state: " + frame);
            assertTrue(frame.contains("\"state\":\"accepted\""), "Первый кадр обязан нести состояние: " + frame);
        }
        assertEquals(0, slots.open(), "Закрытие потока обязано освобождать слот");
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
        assertTrue(
                slotsLocked.await(5, TimeUnit.SECONDS), "Тест обязан удерживать монитор слотов до старта HTTP-запроса");

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
        http.join(TimeUnit.SECONDS.toMillis(5));
        locker.join(TimeUnit.SECONDS.toMillis(5));
        assertFalse(http.isAlive(), "HTTP-запрос обязан завершиться после освобождения слотов");
        assertNull(failure.get(), "HTTP-запрос обязан вернуть ответ, а не упасть исключением: " + failure.get());
        assertEquals(
                200,
                opened.get().status(),
                "HTTP-запрос обязан открывать поток событий: " + opened.get().body());

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
                        + "до клиента, а не потеряться за уже финальной позицией журнала: " + frames);
    }

    @Test
    void events_FifthStream_ReturnsRateLimited() throws Exception {
        String taskId = taskId(postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}"));
        List<RestResponse> open = new ArrayList<>();
        for (int i = 0; i < SseSlots.MAX_STREAMS; i++) {
            RestResponse response = send("GET", "/v1/tasks/" + taskId + "/events", "");
            assertEquals(200, response.status(), "Четыре потока обязаны открываться: " + response.body());
            open.add(response);
        }

        RestResponse fifth = send("GET", "/v1/tasks/" + taskId + "/events", "");

        assertEquals(429, fifth.status(), "Пятый поток обязан получать 429: " + fifth.body());
        assertTrue(fifth.body().contains("rate_limited"), "429 обязан нести code rate_limited: " + fifth.body());
        open.forEach(response -> response.stream().close());
        assertEquals(0, slots.open(), "Все закрытые потоки обязаны освободить слоты");
        RestResponse afterRelease = send("GET", "/v1/tasks/" + taskId + "/events", "");
        assertEquals(
                200,
                afterRelease.status(),
                "После освобождения слота поток обязан открываться снова: " + afterRelease.body());
        afterRelease.stream().close();
    }

    @Test
    void events_InvalidLastEventIdHeader_ReturnsBadRequest() throws Exception {
        String taskId = taskId(postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}"));

        RestResponse response = send("GET", "/v1/tasks/" + taskId + "/events", "", Map.of("Last-Event-ID", "abc"));

        assertEquals(400, response.status(), "Нечисловой Last-Event-ID обязан давать 400: " + response.body());
        assertNull(response.stream(), "400 не должен открывать поток кадров");
        assertEquals(0, slots.open(), "Открытый и отклонённый поток не должен держать слот");
    }

    @Test
    void events_InvalidLastEventId_DoesNotLeakSlot() throws Exception {
        String taskId = taskId(postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}"));
        RestResponse ok = send("GET", "/v1/tasks/" + taskId + "/events", "");
        assertEquals(200, ok.status(), "Первый поток обязан открываться: " + ok.body());

        RestResponse bad = send("GET", "/v1/tasks/" + taskId + "/events", "", Map.of("last-event-id", "не-число"));

        assertEquals(400, bad.status(), "Нечисловой заголовок обязан давать 400: " + bad.body());
        assertEquals(1, slots.open(), "Отклонённый запрос не должен занимать слот: открыт только первый поток");
        ok.stream().close();
    }

    @Test
    void events_StreamingStateAndCancellation_FrameFlow() throws Exception {
        String taskId = taskId(postTasks("\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}"));
        RestResponse response = send("GET", "/v1/tasks/" + taskId + "/events", "");
        try (SseStream stream = response.stream()) {
            String first = stream.nextFrame();
            assertTrue(first.contains("event: state"), "Первый кадр обязана быть state: " + first);

            registry.observe(taskId, TaskState.RUNNING, null, null);

            send("DELETE", "/v1/tasks/" + taskId, "");

            String running = stream.nextFrame();
            assertTrue(
                    running.contains("event: state") && running.contains("running"),
                    "Первым событием обязана идти смена состояния на running: " + running);

            String cancelled = stream.nextFrame();
            assertTrue(
                    cancelled.contains("event: state") && cancelled.contains("cancelled"),
                    "После отмены клиент обязан получить кадр state cancelled: " + cancelled);

            assertNull(stream.nextFrame(), "Закрытый журнал обязан завершать поток значением null");
        }
    }

    @Test
    void plan_ValidNode_ReturnsValidTrue() {
        RestResponse response = send(
                "POST",
                "/v1/plan",
                "{\"plan\":{\"type\":\"script\",\"script\":\"goto\",\"params\":{\"x\":1,\"y\":64,\"z\":2}}}");

        assertEquals(200, response.status(), "Валидный план обязана приниматься сухим прогоном: " + response.body());
        assertEquals("{\"valid\":true}", response.body(), "Сухой прогон обязан отвечать ровно {\"valid\":true}");
        assertEquals(0, registry.size(), "Сухой прогон не должен трогать реестр задач");
        assertTrue(gateway.starts().isEmpty(), "Сухой прогон не должен стартовать духа");
    }

    @Test
    void plan_UnknownScript_ReturnsInvalidPlan() {
        RestResponse response = send("POST", "/v1/plan", "{\"plan\":{\"type\":\"script\",\"script\":\"return_home\"}}");

        assertEquals(422, response.status(), "Неизвестный скрипт обязан давать 422: " + response.body());
        assertTrue(response.body().contains("return_home"), "422 обязан называть скрипт: " + response.body());
        assertTrue(gateway.starts().isEmpty(), "Сухой прогон при 422 не должен стартовать духа");
    }

    @Test
    void plan_MissingField_ReturnsBadRequest() {
        RestResponse response = send("POST", "/v1/plan", "{}");

        assertEquals(400, response.status(), "Отсутствие поля plan обязано давать 400: " + response.body());
        assertTrue(response.body().contains("'plan'"), "400 обязан называть отсутствующее поле: " + response.body());
    }

    private static String jsonField(String json, String field) {
        try {
            return MAPPER.readTree(json).get(field).asText();
        } catch (Exception e) {
            throw new IllegalStateException("Тестовое тело запроса обязано быть валидным JSON: " + json, e);
        }
    }

    @Test
    void create_AcceptedResponse_EchoesRequestIdAndTaskId() throws Exception {
        String requestId = uuid();
        String body =
                "{\"request_id\":\"" + requestId + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}";

        RestResponse response = send("POST", "/v1/bots/" + BOT + "/tasks", body);

        JsonNode parsed = MAPPER.readTree(response.body());
        assertEquals(
                requestId,
                parsed.get("request_id").asText(),
                "Ответ 202 обязан эхать request_id клиента: " + response.body());
        assertTrue(
                parsed.get("task_id").asText().startsWith("t-"),
                "Ответ 202 обязан содержать task_id в формате t-N: " + response.body());
    }

    @Test
    void routes_AllFiveMethods_AreRegistered() {
        Set<String> keys = new HashSet<>();
        new TaskRoutes(registry, gateway, planning, idempotency, slots)
                .routes()
                .forEach(route -> keys.add(route.method() + " " + route.pattern()));

        assertEquals(
                Set.of(
                        "POST /v1/bots/{id}/tasks",
                        "GET /v1/tasks/{id}",
                        "DELETE /v1/tasks/{id}",
                        "GET /v1/tasks/{id}/events",
                        "POST /v1/plan"),
                keys,
                "Чеклист п.7 обязывает ровно пять маршрутов задач");
    }

    @Test
    void routes_UnknownPath_ReturnsNotFound() {
        RestResponse response = send("GET", "/v1/tasks", "");

        assertEquals(404, response.status(), "Необъявленный путь обязан давать 404: " + response.body());
        assertFalse(response.body().contains("stream"), "404 не должен открывать поток");
    }
}
