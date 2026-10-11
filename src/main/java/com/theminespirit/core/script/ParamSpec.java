package com.theminespirit.core.script;

import java.util.Objects;

public record ParamSpec(String name, ParamType type, boolean required) {
    public ParamSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Имя параметра пустое: параметр без имени нельзя передать в план");
        }
        Objects.requireNonNull(type, "Тип параметра не может быть null: по нему валидатор проверяет значение");
    }

    public static ParamSpec integer(String name, boolean required) {
        return new ParamSpec(name, ParamType.INT, required);
    }

    public static ParamSpec number(String name, boolean required) {
        return new ParamSpec(name, ParamType.DOUBLE, required);
    }

    public static ParamSpec text(String name, boolean required) {
        return new ParamSpec(name, ParamType.STRING, required);
    }

    public static ParamSpec textList(String name, boolean required) {
        return new ParamSpec(name, ParamType.STRING_LIST, required);
    }

    public static ParamSpec bool(String name, boolean required) {
        return new ParamSpec(name, ParamType.BOOL, required);
    }

    public static ParamSpec enumeration(String name, boolean required) {
        return new ParamSpec(name, ParamType.ENUM, required);
    }
}
