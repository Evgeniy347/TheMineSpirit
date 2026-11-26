package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.graph.api.HerdCap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class FarmRules {
    private static final Set<String> EVENT_TASKS = Set.of("harvest_crop", "feed_animals");

    public record Crops(int ready, int dry, int empty, int farmland) {
        public Crops {
            if (ready < 0 || dry < 0 || empty < 0 || farmland < 0) {
                throw new IllegalArgumentException("Грядки " + ready + "/" + dry + "/" + empty + "/" + farmland
                        + " не бывают отрицательными: отсутствие — ноль");
            }
        }
    }

    private FarmRules() {}

    public static boolean handlesEvent(String taskId) {
        return EVENT_TASKS.contains(taskId);
    }

    public static Map<String, Integer> herdCounts(Map<String, Integer> species) {
        Map<String, Integer> herds = new LinkedHashMap<>();
        species.forEach((kind, count) -> {
            String herd = herd(kind);
            if (herd != null && count > 0) {
                herds.merge(herd, count, Integer::sum);
            }
        });
        return herds;
    }

    public record Signals(boolean orchardEmpty, boolean hasFeed) {}

    public static Set<String> events(
            Map<String, Integer> herds,
            Map<String, Integer> adults,
            SupplyGraph graph,
            int spirits,
            Crops crops,
            Signals signals) {
        Set<String> events = new LinkedHashSet<>();
        if (crops.ready() > 0) {
            events.add("crop_ready");
        }
        if (crops.dry() > 0) {
            events.add("crop_dry");
        }
        if (crops.empty() > 0) {
            events.add("field_empty");
        }
        boolean hungry = adults.values().stream().mapToInt(Integer::intValue).sum() > 0 && !signals.hasFeed();
        if (hungry) {
            events.add("animal_starving");
        }
        boolean full = herds.entrySet().stream().anyMatch(entry -> {
            HerdCap cap = graph.herdCaps().get(entry.getKey());
            return cap != null && entry.getValue() > 0 && entry.getValue() >= cap.limit(spirits);
        });
        if (full) {
            events.add("pen_full");
        }
        if (signals.orchardEmpty()) {
            events.add("orchard_empty");
        }
        return Set.copyOf(events);
    }

    public static @Nullable String herd(String kind) {
        return switch (kind) {
            case "cow" -> "herd_cow";
            case "sheep" -> "herd_sheep";
            case "pig" -> "herd_pig";
            case "chicken" -> "herd_chicken";
            default -> null;
        };
    }
}
