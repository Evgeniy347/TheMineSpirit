package com.theminespirit.core.perception;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public enum MemoryType {
    RESOURCE,
    DANGER,
    BASE;

    private static final List<String> IDS = Arrays.stream(values()).map(MemoryType::id).toList();

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static List<String> ids() {
        return IDS;
    }

    public static MemoryType parse(String id) {
        Objects.requireNonNull(id, "Тип точки памяти не может быть null: допустимы " + IDS);
        for (MemoryType type : values()) {
            if (type.id().equals(id)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Неизвестный тип точки памяти '" + id + "': допустимы " + IDS
                + " строчными буквами");
    }
}
