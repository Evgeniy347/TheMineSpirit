package com.theminespirit.core.rest;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ServerThreadCall {
    private static final long MILLIS_IN_SECOND = 1000;

    private final Executor serverThread;
    private final AtomicInteger submitted = new AtomicInteger();
    private final Object stopLock = new Object();
    private boolean stopping;

    public ServerThreadCall(Executor serverThread) {
        this.serverThread = serverThread;
    }

    public <T> CompletableFuture<T> submit(Callable<T> task) {
        synchronized (stopLock) {
            if (stopping) {
                throw stoppingError();
            }
            submitted.incrementAndGet();
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            serverThread.execute(() -> run(task, future));
        } catch (RejectedExecutionException e) {
            throw new ApiException(ErrorCode.INTERNAL,
                    "Серверный поток не принял задачу: сервер останавливается или перегружен, повторите запрос позже",
                    Map.of(), e);
        }
        return future;
    }

    public <T> T call(Callable<T> task, Duration timeout) {
        return await(submit(task), timeout);
    }

    public void stop() {
        synchronized (stopLock) {
            stopping = true;
            stopLock.notifyAll();
        }
    }

    public <T> T await(CompletableFuture<T> future, Duration timeout) {
        future.whenComplete((value, error) -> {
            synchronized (stopLock) {
                stopLock.notifyAll();
            }
        });
        long deadline = System.nanoTime() + timeout.toNanos();
        synchronized (stopLock) {
            while (!future.isDone()) {
                if (stopping) {
                    future.cancel(false);
                    throw stoppingError();
                }
                long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remainingMillis <= 0) {
                    future.cancel(false);
                    throw timeoutError(timeout);
                }
                try {
                    stopLock.wait(remainingMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ApiException(ErrorCode.INTERNAL, "Ожидание ответа сервера прервано: мост останавливается",
                            Map.of(), e);
                }
            }
        }
        return result(future);
    }

    public int submittedCount() {
        return submitted.get();
    }

    static String formatSeconds(Duration timeout) {
        long millis = timeout.toMillis();
        if (millis % MILLIS_IN_SECOND == 0) {
            return Long.toString(millis / MILLIS_IN_SECOND);
        }
        return String.format(Locale.ROOT, "%.1f", millis / (double) MILLIS_IN_SECOND);
    }

    private static <T> T result(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (ExecutionException e) {
            throw failure(e.getCause());
        } catch (CancellationException e) {
            throw new ApiException(ErrorCode.INTERNAL, "Задача на серверном потоке отменена до выполнения",
                    Map.of(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.INTERNAL, "Ожидание ответа сервера прервано: мост останавливается",
                    Map.of(), e);
        }
    }

    private static ApiException stoppingError() {
        return new ApiException(ErrorCode.INTERNAL,
                "Сервер останавливается, повторите запрос позже: мост закрывается и больше не читает мир", Map.of());
    }

    private static ApiException timeoutError(Duration timeout) {
        return new ApiException(ErrorCode.INTERNAL,
                "Сервер не ответил за " + formatSeconds(timeout) + " с: серверный тик завис или перегружен, "
                        + "повторите запрос позже и проверьте нагрузку сервера",
                Map.of("timeout_ms", timeout.toMillis()));
    }

    private static <T> void run(Callable<T> task, CompletableFuture<T> future) {
        if (future.isDone()) {
            return;
        }
        try {
            future.complete(task.call());
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
    }

    private static ApiException failure(Throwable cause) {
        if (cause instanceof ApiException api) {
            return api;
        }
        String description = cause.getClass().getName() + ": " + cause.getMessage();
        return new ApiException(ErrorCode.INTERNAL,
                "Задача на серверном потоке завершилась ошибкой: " + description + ". Полный стек — в логе сервера",
                Map.of("cause", description), cause);
    }
}
