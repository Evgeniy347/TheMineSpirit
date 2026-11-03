package com.theminespirit.core.husbandry;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

public enum PetKind {
    WOLF("wolf"),
    CAT("cat"),
    OCELOT("ocelot");

    private final String id;

    PetKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static PetKind parse(String id) {
        Objects.requireNonNull(id, "Вид питомца не может быть null: без него неизвестно, кого приручать");
        String text = id.trim().toLowerCase(Locale.ROOT);
        for (PetKind kind : values()) {
            if (kind.id.equals(text)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Неизвестный вид питомца '" + id + "': допустимы "
                + Arrays.stream(values()).map(PetKind::id).toList());
    }
}
