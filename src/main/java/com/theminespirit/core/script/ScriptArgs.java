package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

final class ScriptArgs {
    private ScriptArgs() {
    }

    static int requireInt(Map<String, Object> params, String name) {
        Object value = require(params, name);
        Integer parsed = toInt(value);
        if (parsed == null) {
            throw new IllegalArgumentException("Параметр '" + name + "' скрипта должен быть целым числом, а получено "
                    + describe(value) + ": маппинг обязан отсечь несовместимый тип до мира");
        }
        return parsed;
    }

    static int optionalInt(Map<String, Object> params, String name, int fallback) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        Object value = params.get(name);
        if (value == null) {
            return fallback;
        }
        Integer parsed = toInt(value);
        if (parsed == null) {
            throw new IllegalArgumentException("Параметр '" + name + "' скрипта должен быть целым числом, а получено "
                    + describe(value) + ": маппинг обязан отсечь несовместимый тип до мира");
        }
        return parsed;
    }

    static String requireString(Map<String, Object> params, String name) {
        Object value = require(params, name);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("Параметр '" + name + "' скрипта должен быть строкой, а получено "
                    + describe(value) + ": маппинг обязан отсечь несовместимый тип до мира");
        }
        return text;
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

    private static Integer toInt(Object value) {
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Long longValue && longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
            return longValue.intValue();
        }
        return null;
    }

    private static String describe(Object value) {
        return value.getClass().getSimpleName();
    }
}