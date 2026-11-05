package com.theminespirit.core.board;

import com.theminespirit.core.building.Preset;
import com.theminespirit.core.building.PresetLibrary;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

public final class UpgradePlan {
    private static final String NAMESPACE = "minecraft:";

    private UpgradePlan() {}

    public static Map<String, Integer> materials(String scheme) {
        Optional<Preset> preset = PresetLibrary.current().byId(scheme);
        if (preset.isEmpty()) {
            throw new IllegalArgumentException(
                    "Схема '" + scheme + "' не найдена в библиотеке пресетов: материалы уровня считать не из чего");
        }
        Map<String, Integer> counts = new TreeMap<>();
        preset.orElseThrow().materials().forEach((item, count) -> counts.merge(shortId(item), count, Integer::sum));
        return Map.copyOf(counts);
    }

    public static Map<String, Integer> required(@Nullable String previousScheme, String nextScheme) {
        Map<String, Integer> next = materials(nextScheme);
        if (previousScheme == null) {
            return next;
        }
        return difference(previousScheme, nextScheme);
    }

    public static Map<String, Integer> difference(String previousScheme, String nextScheme) {
        Map<String, Integer> before = materials(previousScheme);
        Map<String, Integer> after = materials(nextScheme);
        Map<String, Integer> need = new TreeMap<>();
        after.forEach((item, count) -> {
            int rest = count - before.getOrDefault(item, 0);
            if (rest > 0) {
                need.put(item, rest);
            }
        });
        return Map.copyOf(need);
    }

    private static String shortId(String item) {
        return item.startsWith(NAMESPACE) ? item.substring(NAMESPACE.length()) : item;
    }
}
