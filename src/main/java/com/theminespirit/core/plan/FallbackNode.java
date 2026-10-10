package com.theminespirit.core.plan;

import java.util.List;
import java.util.Objects;

public record FallbackNode(List<TaskNode> steps) implements TaskNode {
    public FallbackNode {
        Objects.requireNonNull(steps, "steps не может быть null: fallback без шагов задают пустым списком");
        steps = List.copyOf(steps);
    }
}
