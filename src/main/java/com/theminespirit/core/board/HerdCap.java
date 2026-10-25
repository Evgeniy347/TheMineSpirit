package com.theminespirit.core.board;

import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.reload.DataTables;
import java.util.Objects;
import java.util.Set;

public final class HerdCap {
    public static final Set<String> HERD_KEYS = Set.of("maxPerSpecies", "minBreed");
    public static final int DEFAULT_MAX_PER_SPECIES = 4;
    public static final int DEFAULT_MIN_BREED = 2;

    private HerdCap() {}

    public static int maxPerSpecies() {
        return herdValue("maxPerSpecies", DEFAULT_MAX_PER_SPECIES);
    }

    public static int minBreed() {
        return herdValue("minBreed", DEFAULT_MIN_BREED);
    }

    public static boolean reached(Species species, int housed, int caught) {
        Objects.requireNonNull(species, "Вид лимита не может быть null: без него неизвестно, " + "чей запас считать");
        if (housed < 0 || caught < 0) {
            throw new IllegalArgumentException("Поголовье " + housed + " и пойманное " + caught
                    + " не бывают отрицательными: отрицательное число прятало бы перебор");
        }
        return housed + caught >= maxPerSpecies();
    }

    public static boolean canCapture(Species species, int housed, int caught) {
        return !reached(species, housed, caught);
    }

    static String herdKey(String key) {
        if (!HERD_KEYS.contains(key)) {
            throw new IllegalArgumentException("Неизвестный ключ стада '" + key + "': допустимы " + HERD_KEYS
                    + ", сверьтесь с таблицей herd в board.json");
        }
        return key;
    }

    private static int herdValue(String key, int fallback) {
        Integer value = DataTables.INSTANCE.board().current().herd().get(key);
        return value == null || value < 1 ? fallback : value;
    }
}
