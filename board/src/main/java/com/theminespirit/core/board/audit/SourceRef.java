package com.theminespirit.core.board.audit;

import java.util.Locale;
import java.util.Objects;

public record SourceRef(Kind kind, String subject) {
    public enum Kind {
        BUILDING,
        STREET,
        WALL,
        GROUND,
        MINE,
        NONE
    }

    public static final SourceRef NONE = new SourceRef(Kind.NONE, "");
    public static final SourceRef GROUND = new SourceRef(Kind.GROUND, "");

    public SourceRef {
        Objects.requireNonNull(kind, "Источник клетки не может быть null: без него клетка ничем не вызвана");
        Objects.requireNonNull(subject, "Название источника не может быть null: отсутствие — пустая строка");
        if (kind != Kind.NONE && kind != Kind.GROUND && subject.isBlank()) {
            throw new IllegalArgumentException("Источник " + kind + " без названия: по нему группа находит "
                    + "участок, улицу или спуск, без названия группа повиснет без предмета");
        }
    }

    public static SourceRef building(String plotId) {
        return new SourceRef(Kind.BUILDING, plotId);
    }

    public static SourceRef street(String streetId) {
        return new SourceRef(Kind.STREET, streetId);
    }

    public static SourceRef wall(String segmentId) {
        return new SourceRef(Kind.WALL, segmentId);
    }

    public static SourceRef mine(String part) {
        return new SourceRef(Kind.MINE, part);
    }

    public String text() {
        String name = kind.name().toLowerCase(Locale.ROOT);
        return subject.isBlank() ? name : name + ":" + subject;
    }

    public static SourceRef parse(String text) {
        Objects.requireNonNull(text, "Текст источника не может быть null: его разбирают из данных плана");
        int colon = text.indexOf(':');
        Kind kind = Kind.valueOf((colon < 0 ? text : text.substring(0, colon)).toUpperCase(Locale.ROOT));
        return new SourceRef(kind, colon < 0 ? "" : text.substring(colon + 1));
    }
}
