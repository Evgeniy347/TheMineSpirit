package com.theminespirit.core.rest;

import com.theminespirit.core.plan.TaskNode;
import java.util.Map;

/**
 * Платформенный шлюз задач: на серверном потоке работает с миром, в HTTP-потоке только вызывается.
 */
public interface TaskGateway {
    /**
     * Запуск готового плана у духа.
     *
     * @throws ApiException NOT_FOUND — духа нет, CONFLICT — дух занят задачей вне REST
     */
    void start(String bot, TaskNode plan);

    /**
     * Постановка цели одним переходом на серверный поток: наблюдение, планирование FallbackPlanner и старт.
     *
     * @throws ApiException NOT_FOUND — духа нет, CONFLICT — дух занят, INVALID_PLAN — цель не планируется
     */
    void startGoal(String bot, String goal, Map<String, Object> params);

    /**
     * Остановка задачи духа. Духа нет — тихо: останавливать нечего, задачу уже отменили.
     */
    void cancel(String bot, String taskId);
}
