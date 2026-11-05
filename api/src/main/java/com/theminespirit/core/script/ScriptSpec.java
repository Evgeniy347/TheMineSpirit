package com.theminespirit.core.script;

import java.util.List;
import java.util.Objects;

public record ScriptSpec(
        String id,
        String summary,
        List<ParamSpec> params,
        List<String> preconditions,
        List<String> effects,
        List<ScriptRequirement> requires) {

    public ScriptSpec(
            String id, String summary, List<ParamSpec> params, List<String> preconditions, List<String> effects) {
        this(id, summary, params, preconditions, effects, List.of());
    }

    public ScriptSpec {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Идентификатор скрипта пуст: по id каталог находит скрипт");
        }
        Objects.requireNonNull(summary, "summary не может быть null: описание скрипта показывает ИИ-планировщику");
        params = List.copyOf(
                Objects.requireNonNull(params, "params не может быть null: пустой набор задают пустым списком"));
        preconditions = List.copyOf(Objects.requireNonNull(preconditions, "preconditions не может быть null"));
        effects = List.copyOf(Objects.requireNonNull(effects, "effects не может быть null"));
        requires = List.copyOf(
                Objects.requireNonNull(requires, "requires не может быть null: пустой набор задают пустым списком"));
    }
}
