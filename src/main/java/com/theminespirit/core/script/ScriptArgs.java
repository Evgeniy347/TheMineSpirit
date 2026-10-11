package com.theminespirit.core.script;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class ScriptArgs {
    private ScriptArgs() {
    }

    static int requireInt(Map<String, Object> params, String name) {
        Object value = require(params, name);
        if (!(value instanceof Integer integer)) {
            throw new IllegalArgumentException("Параметр '" + name + "' скрипта должен быть целым числом, а получено "
                    + describe(value) + ": тип параметра обеспечивает валидатор плана до запуска");
        }
        return integer;
    }

    static String requireString(Map<String, Object> params, String name) {
        Object value = require(params, name);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("Параметр '" + name + "' скрипта должен быть строкой, а получено "
                    + describe(value) + ": тип параметра обеспечивает валидатор плана до запуска");
        }
        return text;
    }

    static boolean requireBoolean(Map<String, Object> params, String name) {
        Object value = require(params, name);
        if (!(value instanceof Boolean flag)) {
            throw new IllegalArgumentException("Параметр '" + name + "' скрипта должен быть true или false, а получено "
                    + describe(value) + ": тип параметра обеспечивает валидатор плана до запуска");
        }
        return flag;
    }

    static Optional<List<String>> optionalStringList(Map<String, Object> params, String name) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        Object value = params.get(name);
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof List<?> list)) {
            throw stringListError(name, value);
        }
        for (Object element : list) {
            if (!(element instanceof String)) {
                throw stringListError(name, value);
            }
        }
        return Optional.of(list.stream().map(String.class::cast).toList());
    }

    private static IllegalArgumentException stringListError(String name, Object value) {
        return new IllegalArgumentException("Параметр '" + name + "' скрипта должен быть списком строк, а получено "
                + describe(value) + ": тип параметра обеспечивает валидатор плана до запуска");
    }

    private static Object require(Map<String, Object> params, String name) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        Object value = params.get(name);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Не задан обязательный параметр '" + name + "': без него скрипт не знает, что делать");
        }
        return value;
    }

    private static String describe(Object value) {
        return value.getClass().getSimpleName();
    }
}
