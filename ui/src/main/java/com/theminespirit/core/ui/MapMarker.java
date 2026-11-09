package com.theminespirit.core.ui;

import java.util.Objects;

public record MapMarker(Kind kind, String name, String occupation, int x, int z) {
    public enum Kind {
        SPIRIT,
        PLAYER,
        ROUTE,
        DIRT,
        SAND
    }

    public MapMarker {
        Objects.requireNonNull(kind, "Вид метки не может быть null: духи и игроки рисуются разным цветом");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Имя метки пустое: подпись на карте называет, кто это");
        }
        Objects.requireNonNull(occupation, "Занятие метки не может быть null: без задания — пустая строка");
        if (kind == Kind.PLAYER && !occupation.isEmpty()) {
            throw new IllegalArgumentException("У игрока '" + name + "' занятие '" + occupation
                    + "': занятие берётся из доски только у духов, у игрока его нет");
        }
    }
}
