package com.theminespirit.core.script;

import java.util.Set;

public record ParamSpec(String name, String type, boolean required) {
    public static final String TYPE_INT = "int";
    public static final String TYPE_DOUBLE = "double";
    public static final String TYPE_STRING = "string";
    public static final String TYPE_BOOL = "bool";
    public static final String TYPE_ENUM = "enum";

    private static final Set<String> TYPES = Set.of(TYPE_INT, TYPE_DOUBLE, TYPE_STRING, TYPE_BOOL, TYPE_ENUM);

    public ParamSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Имя параметра пустое: параметр без имени нельзя передать в план");
        }
        if (type == null || !TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "Неизвестный тип параметра '" + name + "': допустимы " + TYPES + ", а получено " + type);
        }
    }
}
