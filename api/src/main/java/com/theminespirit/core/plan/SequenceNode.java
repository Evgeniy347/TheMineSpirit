package com.theminespirit.core.plan;

import java.util.List;
import java.util.Objects;

public record SequenceNode(List<TaskNode> steps) implements TaskNode {
    public SequenceNode {
        Objects.requireNonNull(
                steps, "steps не может быть null: последовательность из пустых шагов задают пустым списком");
        steps = List.copyOf(steps);
    }
}
