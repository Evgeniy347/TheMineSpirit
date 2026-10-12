package com.theminespirit.core.rest;

import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.plan.TaskNode;

/**
 * Платформенный шлюз задач: на серверном потоке работает с миром, в HTTP-потоке только вызывается.
 */
public interface TaskGateway {
    /**
     * Наблюдение мира для планировщика цели.
     *
     * @throws ApiException NOT_FOUND — духа нет
     */
    WorldObservation observation(String bot);

    /**
     * Запуск плана у духа.
     *
     * @throws ApiException NOT_FOUND — духа нет, CONFLICT — дух занят задачей вне REST
     */
    void start(String bot, TaskNode plan);

    /**
     * Остановка задачи духа. Духа нет — тихо: останавливать нечего, задачу уже отменили.
     */
    void cancel(String bot, String taskId);
}
