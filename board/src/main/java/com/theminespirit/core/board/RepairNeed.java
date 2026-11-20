package com.theminespirit.core.board;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class RepairNeed {
    private RepairNeed() {}

    public static Map<String, Integer> merge(Map<String, Integer> consumes, Map<String, Integer> missing) {
        Objects.requireNonNull(consumes, "Потребление билета не может быть null: пустой задают пустой картой");
        Objects.requireNonNull(missing, "Недостача мира не может быть null: пустой задают пустой картой");
        Map<String, Integer> need = new LinkedHashMap<>(consumes);
        missing.forEach((item, count) -> {
            if (count == null || count <= 0 || BuildGap.DIG.equals(item)) {
                return;
            }
            String named = item.startsWith("minecraft:") ? item.substring("minecraft:".length()) : item;
            need.putIfAbsent(named, count);
        });
        return need;
    }
}
