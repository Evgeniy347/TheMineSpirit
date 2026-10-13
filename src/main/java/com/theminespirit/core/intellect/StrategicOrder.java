package com.theminespirit.core.intellect;

import com.theminespirit.core.plan.TaskNode;
import java.util.UUID;

public record StrategicOrder(UUID spirit, String requestId, TaskNode plan, long enqueuedAt, long ttlTicks) {
    public StrategicOrder {
        if (spirit == null) {
            throw new IllegalArgumentException("Задание без духа не ставится: исполняет конкретный дух");
        }
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException(
                    "Задание без requestId не ставится: по нему очередь отсекает дубли стратега");
        }
        if (plan == null) {
            throw new IllegalArgumentException("Задание без плана не ставится: духу нечего исполнять");
        }
        if (enqueuedAt < 0) {
            throw new IllegalArgumentException(
                    "Задание с enqueuedAt = " + enqueuedAt + ": время постановки не может быть отрицательным");
        }
        if (ttlTicks <= 0) {
            throw new IllegalArgumentException(
                    "Задание с ttlTicks = " + ttlTicks + ": срок обязан быть положительным, иначе задание мёртво");
        }
    }
}
