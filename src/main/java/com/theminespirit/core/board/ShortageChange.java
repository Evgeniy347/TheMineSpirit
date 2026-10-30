package com.theminespirit.core.board;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ShortageChange {
    private ShortageChange() {}

    public static boolean improved(Map<String, Integer> before, Map<String, Integer> after) {
        Objects.requireNonNull(before, "before не может быть null: без прежней нехватки не увидеть, стало ли легче");
        Objects.requireNonNull(after, "after не может быть null: без новой нехватки не увидеть, стало ли легче");
        if (before.equals(after)) {
            return false;
        }
        Set<String> items = new HashSet<>(before.keySet());
        items.addAll(after.keySet());
        for (String item : items) {
            if (after.getOrDefault(item, 0) > before.getOrDefault(item, 0)) {
                return false;
            }
        }
        return true;
    }
}
