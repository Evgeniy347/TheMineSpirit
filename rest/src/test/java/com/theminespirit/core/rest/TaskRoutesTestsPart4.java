package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

class TaskRoutesTestsPart4 {
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

    private void observing(Idempotency cache, SseSlots streams) {
        idempotency = cache;
        slots = streams;
        router = Router.of(new TaskRoutes(registry, gateway, planning, cache, streams, board).routes());
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

    @Test
    void create_SecondIdenticalRequestWhileFirstInFlight_ReplaysInsteadOfConflict() throws Exception {
        CountingIdempotencyCache cache = new CountingIdempotencyCache(2);
        observing(cache, slots);
        CountDownLatch firstInStart = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        gateway.runsOnStart(() -> {
            firstInStart.countDown();
            Threads.await(releaseFirst, "Первый запрос обязан ждать шлюза, пока тест не отпустит дубликат");
        });
        String body =
                "{\"request_id\":\"" + uuid() + "\",\"goal\":\"следуй за мной\",\"params\":{\"target\":\"Стейв\"}}";
        AtomicReference<RestResponse> firstOpened = new AtomicReference<>();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        Thread first = postInBackground(body, firstOpened, firstFailure);
        first.start();
        Threads.await(firstInStart, "Первый запрос обязан дойти до шлюза до старта дубликата");

        AtomicReference<RestResponse> secondOpened = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();
        Thread second = postInBackground(body, secondOpened, secondFailure);
        second.start();
        cache.awaitChecks("Дубликат с тем же request_id обязан дойти до проверки request_id раньше, чем "
                + "первый запрос отпустит шлюз, а не получить 409 «дух занят»");

        releaseFirst.countDown();
        Threads.join(first, "Первый запрос обязан завершиться ответом");
        Threads.join(second, "Дубликат обязан завершиться ответом");

        assertNull(
                firstFailure.get(),
                "Первый запрос обязан завершиться ответом: " + firstFailure.get()
                        + " Иначе тест create_SecondIdenticalRequestWhileFirstInFlight_ReplaysInsteadOfConflict перестанет ловить эту поломку. При смене create_SecondIdenticalRequestWhileFirstInFlight_ReplaysInsteadOfConflict проверь это утверждение.");
        assertNull(
                secondFailure.get(),
                "Дубликат обязан завершиться ответом: " + secondFailure.get()
                        + " Иначе тест create_SecondIdenticalRequestWhileFirstInFlight_ReplaysInsteadOfConflict перестанет ловить эту поломку. При смене create_SecondIdenticalRequestWhileFirstInFlight_ReplaysInsteadOfConflict проверь это утверждение.");
        assertEquals(
                202,
                firstOpened.get().status(),
                "Первый запрос обязан приняться: " + firstOpened.get().body()
                        + " Иначе тест create_SecondIdenticalRequestWhileFirstInFlight_ReplaysInsteadOfConflict перестанет ловить эту поломку. При смене create_SecondIdenticalRequestWhileFirstInFlight_ReplaysInsteadOfConflict проверь это утверждение.");
        assertEquals(
                202,
                secondOpened.get().status(),
                "Дубликат обязан получить тот же ответ, а не 409: "
                        + secondOpened.get().body()
                        + " Иначе тест create_SecondIdenticalRequestWhileFirstInFlight_ReplaysInsteadOfConflict перестанет ловить эту поломку. При смене create_SecondIdenticalRequestWhileFirstInFlight_ReplaysInsteadOfConflict проверь это утверждение.");
        assertEquals(
                firstOpened.get().body(),
                secondOpened.get().body(),
                "Идемпотентный повтор обязан отдать исходный ответ дословно"
                        + " Иначе тест create_SecondIdenticalRequestWhileFirstInFlight_ReplaysInsteadOfConflict перестанет ловить эту поломку. При смене create_SecondIdenticalRequestWhileFirstInFlight_ReplaysInsteadOfConflict проверь это утверждение.");
        assertEquals(
                1,
                registry.size(),
                "Дубликат не должен резервировать вторую задачу"
                        + " Иначе тест create_SecondIdenticalRequestWhileFirstInFlight_ReplaysInsteadOfConflict перестанет ловить эту поломку. При смене create_SecondIdenticalRequestWhileFirstInFlight_ReplaysInsteadOfConflict проверь это утверждение.");
    }

    @Test
    void create_FirstRequestFailsWhileDuplicateWaits_SecondGoesItsOwnWay() throws Exception {
        CountingIdempotencyCache cache = new CountingIdempotencyCache(2);
        observing(cache, slots);
        CountDownLatch firstInStart = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger starts = new AtomicInteger();
        gateway.runsOnStart(() -> {
            int attempt = starts.incrementAndGet();
            firstInStart.countDown();
            Threads.await(releaseFirst, "Первый запрос обязан ждать шлюза, пока тест не отпустит дубликат");
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
        Threads.await(firstInStart, "Первый запрос обязан дойти до шлюза до старта дубликата");

        AtomicReference<RestResponse> secondOpened = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();
        Thread second = postInBackground(body, secondOpened, secondFailure);
        second.start();
        cache.awaitChecks("Дубликат обязан дойти до проверки request_id раньше, чем первый запрос провалится");

        releaseFirst.countDown();
        Threads.join(first, "Первый запрос обязан завершиться ответом");
        Threads.join(second, "Дубликат обязан завершиться ответом");

        assertNull(
                firstFailure.get(),
                "Первый запрос обязан завершиться ответом: " + firstFailure.get()
                        + " Иначе тест create_FirstRequestFailsWhileDuplicateWaits_SecondGoesItsOwnWay перестанет ловить эту поломку. При смене create_FirstRequestFailsWhileDuplicateWaits_SecondGoesItsOwnWay проверь это утверждение.");
        assertNull(
                secondFailure.get(),
                "Дубликат обязан завершиться ответом: " + secondFailure.get()
                        + " Иначе тест create_FirstRequestFailsWhileDuplicateWaits_SecondGoesItsOwnWay перестанет ловить эту поломку. При смене create_FirstRequestFailsWhileDuplicateWaits_SecondGoesItsOwnWay проверь это утверждение.");
        assertEquals(
                409,
                firstOpened.get().status(),
                "Провал шлюза обязан пройти клиенту как 409: "
                        + firstOpened.get().body()
                        + " Иначе тест create_FirstRequestFailsWhileDuplicateWaits_SecondGoesItsOwnWay перестанет ловить эту поломку. При смене create_FirstRequestFailsWhileDuplicateWaits_SecondGoesItsOwnWay проверь это утверждение.");
        assertEquals(
                202,
                secondOpened.get().status(),
                "После снятия брони дубликат обязан идти своим ходом, а не ждать вечно: "
                        + secondOpened.get().body()
                        + " Иначе тест create_FirstRequestFailsWhileDuplicateWaits_SecondGoesItsOwnWay перестанет ловить эту поломку. При смене create_FirstRequestFailsWhileDuplicateWaits_SecondGoesItsOwnWay проверь это утверждение.");
        assertEquals(
                1,
                registry.size(),
                "За дубликатом должна остаться одна задача"
                        + " Иначе тест create_FirstRequestFailsWhileDuplicateWaits_SecondGoesItsOwnWay перестанет ловить эту поломку. При смене create_FirstRequestFailsWhileDuplicateWaits_SecondGoesItsOwnWay проверь это утверждение.");
    }
}
