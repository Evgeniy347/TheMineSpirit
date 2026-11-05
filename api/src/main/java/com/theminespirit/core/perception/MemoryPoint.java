package com.theminespirit.core.perception;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public record MemoryPoint(String name, MemoryType type, String dimension, BlockCoord pos) {
    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_DIMENSION_LENGTH = 64;

    public MemoryPoint {
        requireText(name, "name", MAX_NAME_LENGTH, "имя, по которому точку вспоминают");
        Objects.requireNonNull(type, "type не может быть null: точка памяти обязана называть тип");
        requireText(dimension, "dimension", MAX_DIMENSION_LENGTH, "измерение, в котором точку запомнили");
        Objects.requireNonNull(pos, "pos не может быть null: точка памяти обязана называть позицию");
    }

    private static void requireText(String value, String field, int maxLength, String purpose) {
        Objects.requireNonNull(value, field + " не может быть null: " + purpose);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Поле " + field + " пусто: " + purpose + " обязано быть непустым");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException("Поле " + field + " длиной " + value.length() + " длиннее предела "
                    + maxLength + ": память духа ограничена по объёму, сократите значение");
        }
    }
}
