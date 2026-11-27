package com.theminespirit.core.board.audit;

import java.util.Locale;
import java.util.Objects;

public record Access(Kind kind, String reason) {
    public enum Kind {
        SPIRIT,
        FOREIGN,
        PROTECTED,
        TEMP
    }

    public static final Access SPIRIT = new Access(Kind.SPIRIT, "");

    public Access {
        Objects.requireNonNull(kind, "Вид права не может быть null: без него не решить, трогать ли клетку");
        Objects.requireNonNull(reason, "Причина права не может быть null: отсутствие — пустая строка");
        if (kind != Kind.SPIRIT && reason.isBlank()) {
            throw new IllegalArgumentException("Право " + kind + " без причины: чужая или защищённая клетка "
                    + "обязана называть владельца или причину, иначе дух сломает чужое и это не разберётся");
        }
    }

    public static Access foreign(String owner) {
        return new Access(Kind.FOREIGN, owner);
    }

    public static Access temp(String lease) {
        return new Access(Kind.TEMP, lease);
    }

    public boolean touchable() {
        return kind == Kind.SPIRIT || kind == Kind.TEMP;
    }

    public String text() {
        return reason.isBlank() ? kind.name().toLowerCase(Locale.ROOT) : kind + ":" + reason;
    }
}
