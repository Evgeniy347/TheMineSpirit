package com.theminespirit.core.spirit;

import java.util.Collection;
import java.util.Objects;

/**
 * Имена духов: первый свободный номер после общего префикса. Занятые имена приходят
 * снимком из мира, поэтому имя не повторяется у двух живых духов и читается в журнале.
 */
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
