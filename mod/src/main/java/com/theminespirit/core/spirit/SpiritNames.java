package com.theminespirit.core.spirit;

import java.util.Collection;
import java.util.Objects;

public final class SpiritNames {
    public static final String PREFIX = "spirit";

    private SpiritNames() {}

    public static String next(Collection<String> taken) {
        Objects.requireNonNull(taken, "Занятые имена не могут быть null: без них номер не выбрать");
        for (int number = 1; ; number++) {
            String candidate = PREFIX + "-" + number;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
    }
}
