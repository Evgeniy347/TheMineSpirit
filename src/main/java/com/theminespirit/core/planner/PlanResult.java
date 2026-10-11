package com.theminespirit.core.planner;

import com.theminespirit.core.plan.TaskNode;
import java.util.Objects;
import java.util.Optional;

public record PlanResult(TaskNode plan, String source, Optional<String> fallbackReason) {
    public static final String SOURCE_LLM = "llm";
    public static final String SOURCE_FALLBACK = "fallback";

    public PlanResult {
        Objects.requireNonNull(plan, "plan не может быть null: результат планирования обязан нести план");
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Источник плана пуст: по нему видно, кто построил план");
        }
        Objects.requireNonNull(fallbackReason,
                "fallbackReason не может быть null: отсутствие причины задают Optional.empty()");
    }
}
