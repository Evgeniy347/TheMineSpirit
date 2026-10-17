package com.theminespirit.core.crafting;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class CampfireMaterials {
    public static final int STICKS = 3;
    public static final int LOGS = 3;
    public static final int COALS = 1;
    private static final String STICK = "stick";
    private static final List<String> COAL_IDS = List.of("coal", "charcoal");
    private static final List<String> LOG_SUFFIXES = List.of("_log", "_wood", "_stem", "_hyphae");
    private static final List<String> BAMBOO_LOGS = List.of("bamboo_block", "stripped_bamboo_block");

    private CampfireMaterials() {
    }

    public record Cost(Map<String, Integer> consumed) {
        public Cost {
            consumed = Map.copyOf(consumed);
        }
    }

    public static Optional<Cost> cost(Map<String, Integer> inventory) {
        if (inventory == null) {
            throw new IllegalArgumentException("Материалы костра без описи не считаются: пустой запас задают пустой картой");
        }
        Map<String, Integer> left = normalized(inventory);
        Map<String, Integer> consumed = new TreeMap<>();
        if (!take(left, consumed, STICK, STICKS)) {
            return Optional.empty();
        }
        Optional<String> coal = COAL_IDS.stream().filter(id -> left.getOrDefault(id, 0) > 0).findFirst();
        if (coal.isEmpty() || !take(left, consumed, coal.get(), COALS)) {
            return Optional.empty();
        }
        int logsLeft = LOGS;
        for (Map.Entry<String, Integer> entry : new TreeMap<>(left).entrySet()) {
            if (logsLeft <= 0) {
                break;
            }
            if (isLog(entry.getKey()) && entry.getValue() > 0) {
                int moved = Math.min(logsLeft, entry.getValue());
                consumed.merge(entry.getKey(), moved, Integer::sum);
                logsLeft -= moved;
            }
        }
        if (logsLeft > 0) {
            return Optional.empty();
        }
        return Optional.of(new Cost(consumed));
    }

    public static boolean isLog(String item) {
        if (item == null || item.isBlank()) {
            return false;
        }
        String name = normalize(item);
        return BAMBOO_LOGS.contains(name) || LOG_SUFFIXES.stream().anyMatch(name::endsWith);
    }

    private static Map<String, Integer> normalized(Map<String, Integer> inventory) {
        Map<String, Integer> result = new HashMap<>();
        inventory.forEach((item, count) -> {
            if (item != null && count != null && count > 0) {
                result.merge(normalize(item), count, Integer::sum);
            }
        });
        return result;
    }

    private static String normalize(String item) {
        int colon = item.indexOf(':');
        return colon >= 0 ? item.substring(colon + 1) : item;
    }

    private static boolean take(Map<String, Integer> left, Map<String, Integer> consumed, String item, int count) {
        int have = left.getOrDefault(item, 0);
        if (have < count) {
            return false;
        }
        left.put(item, have - count);
        consumed.merge(item, count, Integer::sum);
        return true;
    }
}
