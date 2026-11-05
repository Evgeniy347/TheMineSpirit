package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class GatherDemand {
    private static final String MINECRAFT = "minecraft:";
    private static final String STRIPPED = "stripped_";
    private static final List<String> WOOD_SUFFIXES =
            List.of("_log", "_wood", "_planks", "_sapling", "_leaves", "_roots");

    private GatherDemand() {}

    public record Need(String id, String item, String cause, int count, int priority, BlockCoord at) {
        public Need {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Заявка на добычу без id: доска не отличит её от другой работы");
            }
            if (item == null || item.isBlank()) {
                throw new IllegalArgumentException("Заявка на добычу без предмета: неизвестно, что нести");
            }
            if (count <= 0) {
                throw new IllegalArgumentException(
                        "Заявка на '" + item + "' с количеством " + count + ": нулевой спрос не ставится на доску");
            }
            Objects.requireNonNull(at, "Точка добычи не может быть null: без неё дух не знает, куда идти");
        }

        public Need plus(Need other) {
            if (!item.equals(other.item())) {
                throw new IllegalArgumentException("Нельзя сложить спрос на '" + item + "' и '" + other.item()
                        + "': это разные предметы, их добыча идёт отдельными заявками");
            }
            Need preferred = other.priority() > priority() ? other : this;
            return new Need(
                    id(),
                    item,
                    preferred.cause(),
                    count + other.count(),
                    Math.max(priority(), other.priority()),
                    preferred.at());
        }
    }

    public static List<Need> merge(List<Need> needs) {
        Map<String, Need> merged = new LinkedHashMap<>();
        for (Need need : needs) {
            merged.merge(need.item(), need, Need::plus);
        }
        return List.copyOf(merged.values());
    }

    public static List<Need> onePerWood(List<Need> needs) {
        List<Need> out = new ArrayList<>();
        Map<String, Need> woods = new LinkedHashMap<>();
        for (Need need : needs) {
            String species = wood(need.item());
            if (species == null || need.item().endsWith("_planks") || "fuel".equals(species)) {
                out.add(need);
                continue;
            }
            Need before = woods.get(species);
            if (before == null || strongerWood(need, before)) {
                woods.put(species, need);
            }
        }
        out.addAll(woods.values());
        return List.copyOf(out);
    }

    private static boolean strongerWood(Need candidate, Need current) {
        boolean log = candidate.item().endsWith("_log");
        boolean currentLog = current.item().endsWith("_log");
        if (log != currentLog) {
            return log;
        }
        return candidate.priority() > current.priority();
    }

    public static @Nullable String wood(String item) {
        String name = item.startsWith(MINECRAFT) ? item.substring(MINECRAFT.length()) : item;
        for (String suffix : WOOD_SUFFIXES) {
            if (name.endsWith(suffix) && name.length() > suffix.length()) {
                String species = name.substring(0, name.length() - suffix.length());
                return species.startsWith(STRIPPED) ? species.substring(STRIPPED.length()) : species;
            }
        }
        return null;
    }
}
