package com.theminespirit.core.husbandry;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public enum Species {
    COW,
    SHEEP,
    PIG,
    CHICKEN;

    public static final String ALL = "all";

    private static final List<Species> EVERY = List.of(values());
    private static final List<String> NAMES = Arrays.stream(values())
            .map(species -> species.name().toLowerCase(Locale.ROOT))
            .toList();

    public static List<String> names() {
        return NAMES;
    }

    public static List<Species> all() {
        return EVERY;
    }

    public static Species parse(String species) {
        Objects.requireNonNull(species, "Вид животного не может быть null: без него неизвестно, чем кормить стадо");
        try {
            return valueOf(species.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Неизвестный вид животного '" + species + "': допустимы " + NAMES
                    + ", сверьте значение с каталогом /v1/capabilities", error);
        }
    }

    public static List<Species> parseList(String species) {
        Objects.requireNonNull(species, "Вид животного не может быть null: без него неизвестно, чем кормить стадо");
        if (ALL.equals(species.trim().toLowerCase(Locale.ROOT))) {
            return EVERY;
        }
        return List.of(parse(species));
    }
}
