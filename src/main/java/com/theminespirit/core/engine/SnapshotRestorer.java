package com.theminespirit.core.engine;

import com.theminespirit.core.plan.PlanValidator;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.ScriptCatalog;
import java.util.Objects;

public final class SnapshotRestorer {
    private SnapshotRestorer() {}

    public static TaskSnapshot withValidatedPlan(
            TaskSnapshot snapshot, ScriptCatalog scripts, ConditionCatalog conditions) {
        Objects.requireNonNull(snapshot, "snapshot не может быть null: восстанавливать нечего");
        Objects.requireNonNull(scripts, "Каталог скриптов не может быть null: без него не проверить план снимка");
        Objects.requireNonNull(conditions, "Каталог проверок не может быть null: без него не проверить план снимка");
        if (!snapshot.running()) {
            return snapshot;
        }
        TaskNode validated = PlanValidator.validate(snapshot.plan(), scripts, conditions);
        return new TaskSnapshot(
                snapshot.version(), RunStatus.RUNNING, snapshot.reason(), validated, snapshot.progress());
    }
}
