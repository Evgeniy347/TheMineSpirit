package com.theminespirit.core.board.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class StoreInputs {
    public static final String RESOURCE = "data/theminespirit/board/graph.d/store.json";
    public static final String MINIMUM = "store_minimum";
    public static final String TRANSFER = "store_transfer";
    public static final String SMELT_PUT = "store_smelt_put";
    public static final String SMELT_TAKE = "store_smelt_take";
    public static final String STORAGE_SPACE = "store_storage_space";
    public static final String SORT = "store_sort";
    private static final String NAMESPACE = "minecraft:";

    public record Minimum(int min, int reserve) {
        public Minimum {
            if (min < 0 || reserve < 0) {
                throw new IllegalArgumentException("Минимум " + min + " и резерв " + reserve
                        + " не бывают отрицательными: отсутствие задают нулём");
            }
        }

        public int required() {
            return min + reserve;
        }
    }

    public record Settings(Map<String, Minimum> minimums, int minFreePercent, int carrySurplus, int mixedItems) {
        public Settings {
            minimums = Map.copyOf(
                    Objects.requireNonNull(minimums, "Минимумы склада не могут быть null: без них — пустая карта"));
            if (minFreePercent < 0 || minFreePercent > 100) {
                throw new IllegalArgumentException("Порог свободного склада " + minFreePercent
                        + "% вне [0, 100]: склад не бывает заполнен больше чем полностью");
            }
            if (carrySurplus < 1 || mixedItems < 1) {
                throw new IllegalArgumentException("Порог несомого " + carrySurplus + " и смеси " + mixedItems
                        + " обязаны быть ≥ 1: иначе любая вещь сразу требует переноса");
            }
        }
    }

    private static volatile @Nullable Settings cached;

    private StoreInputs() {}

    public static Settings settings() {
        Settings known = cached;
        if (known == null) {
            synchronized (StoreInputs.class) {
                known = cached;
                if (known == null) {
                    known = parse(JsonResource.parse(JsonResource.readClasspath(RESOURCE), RESOURCE), RESOURCE);
                    cached = known;
                }
            }
        }
        return known;
    }

    static Settings parse(JsonNode root, String origin) {
        Map<String, Minimum> minimums = new LinkedHashMap<>();
        JsonNode nodes = root.path("storeMinimums");
        if (!nodes.isObject() && !nodes.isMissingNode()) {
            throw new IllegalStateException("Файл '" + origin + "': storeMinimums — не объект: "
                    + "ожидается карта «узел → минимальное количество»");
        }
        nodes.properties().forEach(entry -> {
            JsonNode value = entry.getValue();
            int min = value.isNumber() ? value.asInt() : value.path("min").asInt();
            int reserve = value.isObject() ? value.path("reserve").asInt() : 0;
            minimums.put(entry.getKey(), new Minimum(min, reserve));
        });
        return new Settings(
                minimums,
                root.path("storeSpace").path("minFreePercent").asInt(20),
                root.path("storeTransfer").path("carrySurplus").asInt(8),
                root.path("storeSort").path("mixedItems").asInt(6));
    }

    public static GraphFacts.StageReqs stageReqs(
            SupplyGraph graph, List<GraphFacts.Chest> chests, Set<String> foods, Settings settings) {
        Objects.requireNonNull(graph, "Граф не может быть null: по нему считают минимум и содержимое сундуков");
        Objects.requireNonNull(settings, "Настройки склада не могут быть null: без них нет минимума");
        Map<String, Integer> minimum = new LinkedHashMap<>();
        settings.minimums().forEach((node, min) -> {
            requireKnown(graph, node);
            minimum.put(node, min.required());
        });
        Map<String, Integer> stock = stock(chests, foods);
        Map<String, Integer> actual = new LinkedHashMap<>();
        minimum.keySet().forEach(node -> actual.put(node, count(graph, node, stock)));
        return new GraphFacts.StageReqs(minimum, actual);
    }

    public static Set<String> events(GraphFacts facts) {
        Settings settings = settings();
        Set<String> events = new LinkedHashSet<>();
        if (deficit(facts.stageReqs())) {
            events.add(MINIMUM);
        }
        if (transfer(facts, settings)) {
            events.add(TRANSFER);
        }
        if (facts.stations().values().stream().anyMatch(StoreInputs::smelting)) {
            boolean put = false;
            boolean take = false;
            for (GraphFacts.Station station : facts.stations().values()) {
                if (!smelting(station)) {
                    continue;
                }
                put |= blank(station.input());
                take |= !blank(station.output());
            }
            if (put && fuel(facts.chests())) {
                events.add(SMELT_PUT);
            }
            if (take) {
                events.add(SMELT_TAKE);
            }
        }
        if (storageFull(facts.chests(), settings)) {
            events.add(STORAGE_SPACE);
        }
        if (mixed(facts.chests(), settings)) {
            events.add(SORT);
        }
        return Set.copyOf(events);
    }

    private static Map<String, Integer> stock(List<GraphFacts.Chest> chests, Set<String> foods) {
        Map<String, Integer> raw = new LinkedHashMap<>();
        chests.forEach(chest -> chest.contents().forEach((item, count) -> raw.merge(item, count, Integer::sum)));
        return GraphNames.stock(raw, foods);
    }

    private static int count(SupplyGraph graph, String node, Map<String, Integer> stock) {
        if (graph.isGroup(node)) {
            return graph.members(node).stream()
                    .mapToInt(member -> stock.getOrDefault(member, 0))
                    .sum();
        }
        if (graph.isTool(node)) {
            int split = node.lastIndexOf('_');
            int rank = graph.rank(node.substring(0, split));
            String kind = node.substring(split + 1);
            int total = 0;
            for (Map.Entry<String, Integer> entry : stock.entrySet()) {
                if (GraphKits.of(List.of(entry.getKey()), graph).getOrDefault(kind, -1) >= rank) {
                    total += entry.getValue();
                }
            }
            return total;
        }
        return stock.getOrDefault(node, 0);
    }

    private static void requireKnown(SupplyGraph graph, String node) {
        if (!graph.isGroup(node) && !graph.isTool(node) && !graph.items().containsKey(node)) {
            throw new IllegalStateException("Настройки склада просят узел " + node
                    + ", которого нет в графе: добавьте предмет, группу или инструмент");
        }
    }

    private static boolean deficit(GraphFacts.StageReqs reqs) {
        return reqs.minimum().entrySet().stream()
                .anyMatch(entry -> reqs.actual().getOrDefault(entry.getKey(), 0) < entry.getValue());
    }

    private static boolean transfer(GraphFacts facts, Settings settings) {
        boolean room = facts.chests().stream().anyMatch(chest -> chest.freeSlots() > 0);
        if (!room) {
            return false;
        }
        return facts.spirits().values().stream()
                .anyMatch(spirit ->
                        spirit.inventory().values().stream().anyMatch(count -> count >= settings.carrySurplus()));
    }

    private static boolean smelting(GraphFacts.Station station) {
        String id = station.id().toLowerCase(Locale.ROOT);
        String type = station.type().toLowerCase(Locale.ROOT);
        return id.contains("furnace") || type.contains("furnace");
    }

    private static boolean blank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    private static boolean fuel(List<GraphFacts.Chest> chests) {
        return chests.stream()
                .anyMatch(chest -> chest.contents().keySet().stream().anyMatch(StoreInputs::fuel));
    }

    private static boolean fuel(String item) {
        String name = item.startsWith(NAMESPACE) ? item.substring(NAMESPACE.length()) : item;
        return "coal".equals(name) || "charcoal".equals(name) || name.endsWith("_log") || name.endsWith("_planks");
    }

    private static boolean storageFull(List<GraphFacts.Chest> chests, Settings settings) {
        int free = 0;
        int slots = 0;
        for (GraphFacts.Chest chest : chests) {
            free += chest.freeSlots();
            slots += chest.totalSlots();
        }
        return slots > 0 && free * 100 / slots < settings.minFreePercent();
    }

    private static boolean mixed(List<GraphFacts.Chest> chests, Settings settings) {
        return chests.size() > 1
                && chests.stream().anyMatch(chest -> chest.contents().size() > settings.mixedItems());
    }
}
