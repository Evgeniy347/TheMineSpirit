package com.theminespirit.core.tactics;

import java.util.Objects;

public final class FactChecks {
    private FactChecks() {}

    public static double range(String field, double value, double min, double max) {
        if (!Double.isFinite(value)) {
            throw new InvalidFieldException(
                    field,
                    field + " = " + value + ": число должно быть конечным (не NaN и не бесконечность), допустимо от "
                            + min + " до " + max);
        }
        if (value < min || value > max) {
            throw new InvalidFieldException(field, field + " = " + value + ": допустимо от " + min + " до " + max);
        }
        return value;
    }

    public static int range(String field, int value, int min, int max) {
        if (value < min || value > max) {
            throw new InvalidFieldException(field, field + " = " + value + ": допустимо от " + min + " до " + max);
        }
        return value;
    }

    public static <T> T required(String field, T value) {
        return Objects.requireNonNull(value, field + " не может быть null: факт обязателен");
    }
}
