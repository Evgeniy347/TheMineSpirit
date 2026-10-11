package com.theminespirit.core.engine;

import com.theminespirit.core.plan.TaskNode;
import java.util.Objects;

public record TaskSnapshot(int version, RunStatus status, String reason, TaskNode plan, TaskProgress progress) {
    public static final int VERSION = 1;

    public TaskSnapshot {
        if (version < 1) {
            throw new IllegalArgumentException("Версия снимка " + version + " меньше 1: версия — положительное число, "
                    + "по нему читатель выбирает разбор");
        }
        Objects.requireNonNull(status, "status не может быть null: по нему читатель решает, продолжать задачу или нет");
        if (status == RunStatus.RUNNING) {
            Objects.requireNonNull(plan,
                    "RUNNING-снимок без плана: продолжать нечего, восстановление упрётся в null");
            Objects.requireNonNull(progress,
                    "RUNNING-снимок без прогресса: движок не знает, с какого шага продолжать");
        } else if (plan != null || progress != null) {
            throw new IllegalArgumentException("Завершённый снимок (" + status
                    + ") не несёт план и прогресс: восстановление обязано ничего не запускать, а лишние поля "
                    + "привели бы к перезапуску отменённой задачи");
        }
    }

    public boolean running() {
        return status == RunStatus.RUNNING;
    }
}
