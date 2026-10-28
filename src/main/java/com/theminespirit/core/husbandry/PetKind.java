package com.theminespirit.core.husbandry;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public enum PetKind {
    WOLF("wolf", false),
    CAT("cat", false),
    OCELOT("ocelot", true);

    private final String id;
    private final boolean trust;

    PetKind(String id, boolean trust) {
        this.id = id;
        this.trust = trust;
    }

    public String id() {
        return id;
    }

    public boolean trust() {
        return trust;
    }

    public static List<PetKind> all() {
        return List.of(values());
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
