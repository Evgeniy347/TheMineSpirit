package com.theminespirit.core.plan;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record ConditionNode(
        String check,
        Map<String, Object> params,
        TaskNode thenStep,
        @Nullable TaskNode elseStep) implements TaskNode {
    public ConditionNode {
        if (check == null || check.isBlank()) {
            throw new IllegalArgumentException("Имя проверки в условии пусто: по нему выбирается ветка");
        }
        params = Map.copyOf(
                Objects.requireNonNull(params, "params не может быть null: пустой набор задают пустой картой"));
        Objects.requireNonNull(thenStep, "thenStep не может быть null: у условия обязана быть ветка then");
    }
}
