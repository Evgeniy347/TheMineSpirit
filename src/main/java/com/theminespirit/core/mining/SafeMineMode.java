package com.theminespirit.core.mining;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Форма безопасной шахты: спуск лесенкой вниз или горизонтальная штольня.
 * Ширина прохода у обеих — один блок, высота — два.
 */
public enum SafeMineMode {
    STAIRCASE,
    TUNNEL;

    private static final List<String> NAMES = Arrays.stream(values())
            .map(mode -> mode.name().toLowerCase(Locale.ROOT))
            .toList();

    public static List<String> names() {
        return NAMES;
    }

    public static SafeMineMode parse(String id) {
        Objects.requireNonNull(id, "Форма шахты не может быть null: без неё неизвестно, копать вниз или вбок");
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Неизвестная форма шахты '" + id + "': допустимы " + NAMES, error);
        }
    }
}
