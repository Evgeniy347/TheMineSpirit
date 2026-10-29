package com.theminespirit.core.board;

import java.util.Map;
import java.util.Objects;

public final class RepairReopen {
    private RepairReopen() {}

    public static boolean shouldReopen(Map<String, Integer> before, Map<String, Integer> after) {
        Objects.requireNonNull(before, "Прежняя нехватка ремонта не может быть null: пустую задают пустой картой");
        Objects.requireNonNull(after, "Новая нехватка ремонта не может быть null: пустую задают пустой картой");
        return !before.equals(after);
    }
}
