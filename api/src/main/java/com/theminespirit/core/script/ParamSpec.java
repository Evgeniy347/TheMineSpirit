package com.theminespirit.core.script;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record ParamSpec(
        String name,
        ParamType type,
        boolean required,
        @Nullable Double min,
        @Nullable Double max,
        @Nullable List<String> values,
        @Nullable Object defaultValue) {

    public ParamSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Имя параметра пустое: параметр без имени нельзя передать в план");
        }
        Objects.requireNonNull(type, "Тип параметра не может быть null: по нему валидатор проверяет значение");
        values = values == null ? null : List.copyOf(values);
        if (type == ParamType.ENUM) {
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException("У enum-параметра '" + name
                        + "' нет допустимых значений: без списка валидатор не отличит верное значение от опечатки");
            }
        } else if (values != null) {
            throw new IllegalArgumentException("У параметра '" + name + "' типа " + type
                    + " задан список значений: values допустим только для ENUM");
        }
        if (min != null && max != null && min > max) {
            throw new IllegalArgumentException("У параметра '" + name + "' min=" + min + " больше max=" + max
                    + ": такой диапазон не примет ни одно значение");
        }
        if (type != ParamType.INT && type != ParamType.DOUBLE && (min != null || max != null)) {
            throw new IllegalArgumentException("У параметра '" + name + "' типа " + type
                    + " заданы min/max: границы допустимы только для числовых типов");
        }
        if (defaultValue != null
                && type == ParamType.ENUM
                && (values == null || !values.contains(defaultValue.toString()))) {
            throw new IllegalArgumentException("Значение по умолчанию '" + defaultValue + "' параметра '" + name
                    + "' отсутствует в списке допустимых " + values);
        }
    }

    public static ParamSpec integer(String name, boolean required) {
        return new ParamSpec(name, ParamType.INT, required, null, null, null, null);
    }

    public static ParamSpec integer(String name, boolean required, long min, long max) {
        return new ParamSpec(name, ParamType.INT, required, (double) min, (double) max, null, null);
    }

    public static ParamSpec integer(String name, boolean required, long min, long max, int defaultValue) {
        return new ParamSpec(name, ParamType.INT, required, (double) min, (double) max, null, defaultValue);
    }

    public static ParamSpec number(String name, boolean required, double min, double max) {
        return new ParamSpec(name, ParamType.DOUBLE, required, min, max, null, null);
    }

    public static ParamSpec text(String name, boolean required) {
        return new ParamSpec(name, ParamType.STRING, required, null, null, null, null);
    }

    public static ParamSpec text(String name, boolean required, String defaultValue) {
        return new ParamSpec(name, ParamType.STRING, required, null, null, null, defaultValue);
    }

    public static ParamSpec textList(String name, boolean required) {
        return new ParamSpec(name, ParamType.STRING_LIST, required, null, null, null, null);
    }

    public static ParamSpec bool(String name, boolean required) {
        return new ParamSpec(name, ParamType.BOOL, required, null, null, null, null);
    }

    public static ParamSpec bool(String name, boolean required, boolean defaultValue) {
        return new ParamSpec(name, ParamType.BOOL, required, null, null, null, defaultValue);
    }

    public static ParamSpec enumeration(String name, boolean required, List<String> values) {
        return new ParamSpec(name, ParamType.ENUM, required, null, null, values, null);
    }

    public static ParamSpec enumeration(String name, boolean required, List<String> values, String defaultValue) {
        return new ParamSpec(name, ParamType.ENUM, required, null, null, values, defaultValue);
    }
}
