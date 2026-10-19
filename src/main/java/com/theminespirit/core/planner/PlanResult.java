package com.theminespirit.core.planner;

import com.theminespirit.core.plan.TaskNode;
import java.util.Objects;
import java.util.Optional;

public record PlanResult(TaskNode plan, PlanSource source, Optional<String> fallbackReason) {
    public PlanResult {
        Objects.requireNonNull(plan, "plan не может быть null: результат планирования обязан нести план");
        Objects.requireNonNull(source, "Источник плана не может быть null: по нему видно, кто построил план");
        Objects.requireNonNull(
                fallbackReason, "fallbackReason не может быть null: отсутствие причины задают Optional.empty()");
    }
}
