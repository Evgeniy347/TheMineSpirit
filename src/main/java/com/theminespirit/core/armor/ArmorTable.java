package com.theminespirit.core.armor;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.observation.IdFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class ArmorTable {
    private static final String RESOURCE_PATH = "data/theminespirit/armor/armor_table.json";
    private static final Set<String> ROOT_FIELDS = Set.of("material_order", "worn_fraction");
    private static final Set<String> MATERIAL_FIELDS = Set.of("match", "rank");

    private final String origin;
    private final List<MaterialRule> materialOrder;
    private final double wornFraction;
    private final Map<String, Integer> rankCache = new ConcurrentHashMap<>();

    private ArmorTable(String origin, List<MaterialRule> materialOrder, double wornFraction) {
        this.origin = origin;
        this.materialOrder = List.copyOf(materialOrder);
        this.wornFraction = wornFraction;
    }

    public static ArmorTable load() {
        return load(RESOURCE_PATH);
    }

    public static ArmorTable load(String resourcePath) {
        return parse(JsonResource.readClasspath(resourcePath), resourcePath);
    }

    public static ArmorTable parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, ROOT_FIELDS, origin, "файла материалов брони");
        List<MaterialRule> order = parseOrder(root.get("material_order"), origin);
        double worn = parseWornFraction(root.get("worn_fraction"), origin);
        return new ArmorTable(origin, order, worn);
    }

    public int materialRank(String item) {
        Objects.requireNonNull(item, "Предмет не назван: порядок материала определяется по id");
        return rankCache.computeIfAbsent(item, this::matchRank);
    }

    public double wornFraction() {
        return wornFraction;
    }

    public String origin() {
        return origin;
    }

    public List<MaterialRule> materialOrder() {
        return materialOrder;
    }

    private int matchRank(String item) {
        for (MaterialRule rule : materialOrder) {
            if (rule.filter().matches(item)) {
                return rule.rank();
            }
        }
        return -1;
    }

    private static List<MaterialRule> parseOrder(JsonNode node, String origin) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new IllegalStateException("Файл материалов брони '" + origin
                    + "' без списка material_order: без порядка материалов нечем разрешить ничью "
                    + "между равными по очкам и крепости предметами");
        }
        List<JsonNode> nodes = JsonResource.array(node, origin, "списка material_order");
        List<MaterialRule> rules = new ArrayList<>(nodes.size());
        for (int index = 0; index < nodes.size(); index++) {
            JsonNode ruleNode = nodes.get(index);
            if (!ruleNode.isObject()) {
                throw new IllegalStateException("Файл материалов брони '" + origin + "': правило №" + index
                        + " — не объект с полями match и rank");
            }
            JsonResource.checkFields(ruleNode, MATERIAL_FIELDS, origin, "правила №" + index);
            int rank = JsonResource.integer(ruleNode.get("rank"), origin, "поля rank правила №" + index);
            if (rank < 0) {
                throw new IllegalStateException("Файл материалов брони '" + origin + "': ранг правила №" + index
                        + " равен " + rank + ": ранг задаёт «лучше», отрицательный ранг сломал бы порядок");
            }
            rules.add(rule(JsonResource.strings(ruleNode.get("match"), origin,
                    "поля match правила №" + index), rank, origin, "правила №" + index));
        }
        return rules;
    }

    private static double parseWornFraction(JsonNode node, String origin) {
        if (node == null || !node.isNumber()) {
            throw new IllegalStateException("Файл материалов брони '" + origin + "': у поля worn_fraction "
                    + "ожидается число: порог «почти сломан» задаётся долей прочности от 0 до 1");
        }
        double value = node.asDouble();
        if (value <= 0 || value >= 1) {
            throw new IllegalStateException("Файл материалов брони '" + origin + "': worn_fraction равен " + value
                    + ": порог задаётся строго между 0 и 1, иначе почти сломанным был бы любой предмет "
                    + "или ни один");
        }
        return value;
    }

    private static MaterialRule rule(List<String> match, int rank, String origin, String what) {
        try {
            return new MaterialRule(match, rank);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл материалов брони '" + origin + "': " + what + " неверно — "
                    + error.getMessage(), error);
        }
    }

    public record MaterialRule(List<String> match, int rank) {
        public MaterialRule {
            Objects.requireNonNull(match, "Список шаблонов материала не может быть null: пустой список — ошибка");
            if (match.isEmpty()) {
                throw new IllegalArgumentException("У правила материала пустой match: правило без шаблонов "
                        + "не описывает ни один предмет");
            }
            if (rank < 0) {
                throw new IllegalArgumentException("Ранг материала " + rank + " отрицателен: "
                        + "ранг задаёт «лучше», отрицательный сломал бы порядок");
            }
            IdFilter.of(match);
            match = List.copyOf(match);
        }

        public IdFilter filter() {
            return IdFilter.of(match);
        }
    }
}