package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ServerThreadCallTests {
    private static final Duration LONG_ENOUGH = Duration.ofSeconds(10);
    private static final Duration WITHOUT_TIMEOUT = Duration.ofDays(3650);
    private static final Duration SHORT = Duration.ofMillis(100);
    private static final String SERVER_THREAD_NAME = "test-server-thread";

    private final ExecutorService serverThread = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, SERVER_THREAD_NAME);
        thread.setDaemon(true);
        return thread;
    });

    @AfterEach
    void stopServerThread() {
        serverThread.shutdownNow();
    }

    private static final class QueuedExecutor implements Executor {
        private final Queue<Runnable> queue = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            queue.add(task);
        }

        void runAll() {
            while (!queue.isEmpty()) {
                queue.poll().run();
            }
        }
    }

    @Test
    void call_TaskOnRealExecutor_ReturnsResult() {
        ServerThreadCall call = new ServerThreadCall(serverThread);

        int result = call.call(() -> 42, LONG_ENOUGH);

        assertEquals(42, result, "Результат задачи с серверного потока обязан дойти до HTTP-потока без потерь");
    }

    @Test
    void call_Task_RunsOnExecutorThreadNotOnCaller() {
        ServerThreadCall call = new ServerThreadCall(serverThread);
        Thread caller = Thread.currentThread();

        String ranOn = call.call(() -> Thread.currentThread().getName(), LONG_ENOUGH);

        assertEquals(
                SERVER_THREAD_NAME,
                ranOn,
                "Задача обязана выполняться на серверном потоке: HTTP-потоки не имеют права трогать мир");
        assertFalse(caller.getName().equals(ranOn), "Поток вызывающего не должен исполнять задачу сам");
    }

    @Test
    void await_ExecutorNeverRunsTask_ThrowsInternalNamingTimeout() {
        ServerThreadCall call = new ServerThreadCall(new QueuedExecutor());

        ApiException error = assertThrows(
                ApiException.class,
                () -> call.call(() -> 1, SHORT),
                "Зависший тик не должен вешать запрос навсегда: после тайм-аута клиент получает ошибку");

        assertEquals(
                ErrorCode.INTERNAL,
                error.error().code(),
                "Тайм-аут серверного потока — ошибка internal (500), а не 404 или 400");
        assertTrue(
                error.getMessage().contains("Сервер не ответил за 0.1 с"),
                "Сообщение называет, сколько секунд ждали: " + error.getMessage());
    }

    @Test
    void await_TaskStartsAfterTimeout_BodyIsSkipped() {
        QueuedExecutor executor = new QueuedExecutor();
        ServerThreadCall call = new ServerThreadCall(executor);
        AtomicInteger bodyRuns = new AtomicInteger();

        assertThrows(
                ApiException.class,
                () -> call.call(bodyRuns::incrementAndGet, SHORT),
                "Без запуска задача обязана упереться в тайм-аут");
        executor.runAll();

        assertEquals(
                0,
                bodyRuns.get(),
                "Задача, не стартовавшая до тайм-аута, обязана пропускаться: иначе зависший тик выполнит "
                        + "накопившиеся запросы уже ушедших клиентов и снова встанет");
    }

    @Test
    void await_TaskThrowsRuntimeException_KeepsCauseInErrorAndDetails() {
        QueuedExecutor executor = new QueuedExecutor();
        ServerThreadCall call = new ServerThreadCall(executor);
        IllegalStateException boom = new IllegalStateException("мир сломан");
        CompletableFuture<Integer> future = call.submit(() -> {
            throw boom;
        });
        executor.runAll();

        ApiException error = assertThrows(
                ApiException.class,
                () -> call.await(future, LONG_ENOUGH),
                "Исключение задачи не должно пропадать молча: клиент обязан получить internal");

        assertEquals(ErrorCode.INTERNAL, error.error().code(), "Падение задачи — internal (500)");
        assertSame(boom, error.getCause(), "Причина исключения сохраняется для лога");
        assertEquals(
                "java.lang.IllegalStateException: мир сломан",
                error.error().details().get("cause"),
                "details.cause называет тип и сообщение исключения: "
                        + error.error().details());
    }

    @Test
    void await_TaskThrowsApiException_PassesItUnchanged() {
        ServerThreadCall call = new ServerThreadCall(serverThread);
        ApiException original = new ApiException(ErrorCode.NOT_FOUND, "нет такого духа");

        ApiException error = assertThrows(
                ApiException.class,
                () -> call.call(
                        () -> {
                            throw original;
                        },
                        LONG_ENOUGH),
                "Ошибка API из задачи обязана дойти до клиента как есть");

        assertSame(original, error, "not_found из задачи не должен превращаться в internal");
    }

    @Test
    void submittedCount_EachSubmit_CountsOneHop() {
        ServerThreadCall call = new ServerThreadCall(serverThread);

        call.call(() -> 1, LONG_ENOUGH);
        call.call(() -> 2, LONG_ENOUGH);

        assertEquals(
                2,
                call.submittedCount(),
                "Счётчик считает переходы на серверный поток: по одному на запрос, иначе тест на масштаб слеп");
    }

    @Test
    void submit_RejectedByExecutor_ThrowsInternalApiException() {
        ServerThreadCall call = new ServerThreadCall(task -> {
            throw new RejectedExecutionException("сервер остановлен");
        });

        ApiException error = assertThrows(
                ApiException.class,
                () -> call.submit(() -> 1),
                "Остановленный сервер не принимает задачи: клиент обязан получить внятную ошибку");

        assertEquals(ErrorCode.INTERNAL, error.error().code(), "Отказ исполнителя — internal (500)");
    }

    @Test
    void await_StopWhileTaskNeverRuns_ThrowsNamingShutdown() throws Exception {
        ServerThreadCall call = new ServerThreadCall(new QueuedExecutor());
        CompletableFuture<Integer> future = call.submit(() -> 1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch awaiting = new CountDownLatch(1);
        Thread waiter = new Thread(
                () -> {
                    awaiting.countDown();
                    try {
                        call.await(future, WITHOUT_TIMEOUT);
                    } catch (Throwable e) {
                        failure.set(e);
                    }
                },
                "test-awaiting-thread");
        waiter.setDaemon(true);
        waiter.start();

        assertTrue(
                awaiting.await(LONG_ENOUGH.toMillis(), TimeUnit.MILLISECONDS),
                "Ожидальец обязан дойти до await: иначе тест проверяет запуск потока, а не остановку сервера");
        call.stop();
        waiter.join(LONG_ENOUGH.toMillis());

        assertFalse(
                waiter.isAlive(),
                "Остановка сервера обязана будить ждущий запрос через сигнал остановки: "
                        + "само-пробуждение по тайм-ауту исключено (WITHOUT_TIMEOUT), поэтому живой "
                        + "ожидальец означает, что stop() его не разбудил и запрос висит");
        Throwable thrown = failure.get();
        assertTrue(
                thrown instanceof ApiException,
                "Ждущий запрос при остановке обязан кидать ApiException, а не молча возвращаться или "
                        + "умирать с другим: фактически " + thrown);
        assertTrue(
                thrown.getMessage().contains("останавливается"),
                "Сообщение обязано называть остановку сервера, а не истечение тайм-аута: " + thrown.getMessage());
    }

    @Test
    void submit_AfterStop_ThrowsImmediatelyNamingShutdown() {
        ServerThreadCall call = new ServerThreadCall(new QueuedExecutor());
        call.stop();

        ApiException error = assertThrows(
                ApiException.class,
                () -> call.submit(() -> 1),
                "После остановки новые задачи не принимаются: иначе HTTP-поток снова начнёт читать мир");

        assertEquals(ErrorCode.INTERNAL, error.error().code(), "Отказ после остановки — internal (500)");
        assertTrue(
                error.getMessage().contains("останавливается"),
                "Сообщение обязано называть остановку сервера: " + error.getMessage());
    }

    @Test
    void formatSeconds_WholeAndFractionalDurations_ReadNaturally() {
        assertEquals(
                "5",
                ServerThreadCall.formatSeconds(Duration.ofSeconds(5)),
                "Целые секунды пишутся без дроби: «не ответил за 5 с»");
        assertEquals(
                "1.5",
                ServerThreadCall.formatSeconds(Duration.ofMillis(1500)),
                "Дробные секунды — с одним знаком и точкой независимо от локали");
    }
}
