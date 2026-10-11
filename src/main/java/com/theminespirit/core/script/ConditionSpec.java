package com.theminespirit.core.script;

import java.util.List;
import java.util.Objects;

public record ConditionSpec(String id, String summary, List<ParamSpec> params) {
    public ConditionSpec {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Идентификатор проверки пуст: по нему condition находит вычислитель");
        }
        Objects.requireNonNull(summary, "summary не может быть null: описание проверки видит ИИ-планировщик");
        params = List.copyOf(
                Objects.requireNonNull(params, "params не может быть null: пустой набор задают пустым списком"));
    }
}
