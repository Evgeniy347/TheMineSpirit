package com.theminespirit.core.plan;

import java.util.List;
import java.util.Objects;

public record ParallelNode(ParallelMode mode, List<TaskNode> steps) implements TaskNode {
    public ParallelNode {
        Objects.requireNonNull(mode, "mode не может быть null: режим решает, когда параллельный узел завершён");
        Objects.requireNonNull(steps, "steps не может быть null: шаги параллельного узла задают списком");
        steps = List.copyOf(steps);
    }
}
