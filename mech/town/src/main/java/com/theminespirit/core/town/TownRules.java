package com.theminespirit.core.town;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.TableSlot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public record TownRules(Ground ground, Streets streets, Spacing spacing, List<TownEntry> buildings) {
    public static final String RESOURCE = "data/theminespirit/board/town.json";
    private static final TableSlot<TownRules> SOURCE = new TableSlot<>(TownRules::load);
    private static final Set<String> ROOT = Set.of("ground", "streets", "spacing", "buildings");
    private static final Set<String> GROUND = Set.of("area", "probeRadius", "cliffStep", "slopeWeight", "slopeMax");
    private static final Set<String> STREETS = Set.of("main", "side", "ring", "ringInset", "blockSize");
    private static final Set<String> SPACING = Set.of("gap", "stageMargin", "chestFreePercent", "fenceStrip");
    private static final Set<String> ENTRY =
            Set.of("id", "name", "width", "depth", "quarter", "level", "rotate", "field");

    public record Ground(int area, int probeRadius, int cliffStep, int slopeWeight, int slopeMax) {
        public Ground {
            if (area < 1 || probeRadius < 1 || cliffStep < 1 || slopeWeight < 0 || slopeMax < 0) {
                throw new IllegalArgumentException("Рельеф плана: площадь " + area + ", радиус обмера " + probeRadius
                        + ", обрыв " + cliffStep + ", вес и предел уклона " + slopeWeight + "/" + slopeMax
                        + ": площадь, радиус и обрыв положительны, уклон не отрицателен");
            }
            long side = 2L * probeRadius + 1;
            if (side * side < area) {
                throw new IllegalArgumentException("Радиус обмера " + probeRadius + " даёт " + side * side
                        + " колонок меньше площади " + area + ": участок не уместится в обмер");
            }
        }
    }

    public record Streets(int main, int side, int ring, int ringInset, int blockSize) {
        public Streets {
            if (main < 1 || side < 1 || ring < 1 || ringInset < 1 || blockSize <= main) {
                throw new IllegalArgumentException("Улицы плана: главная " + main + ", боковая " + side
                        + ", кольцевая " + ring + ", отступ кольца " + ringInset + ", квартал " + blockSize
                        + ": ширины и отступ положительны, квартал шире главной улицы");
            }
        }
    }

    public record Spacing(int gap, int stageMargin, int chestFreePercent, int fenceStrip) {
        public Spacing {
            if (fenceStrip < 0 || fenceStrip > stageMargin + 1) {
                throw new IllegalArgumentException("Полоса ограды " + fenceStrip + " клеток по обе стороны линии: "
                        + "от 0 до поля ограды + 1 (" + (stageMargin + 1) + "), иначе забор встаёт на разные высоты "
                        + "или выравнивание уходит за участок");
            }
            if (gap < 0 || stageMargin < 1 || chestFreePercent < 0 || chestFreePercent > 100) {
                throw new IllegalArgumentException("Отступы плана: между участками " + gap + ", поле ограды этапа "
                        + stageMargin + ", свободно в сундуках " + chestFreePercent
                        + "%: отступ не отрицателен, поле хотя бы 1, доля 0..100");
            }
        }
    }

    public TownRules {
        Objects.requireNonNull(ground, "Правила рельефа плана не могут быть null");
        Objects.requireNonNull(streets, "Правила улиц плана не могут быть null");
        Objects.requireNonNull(spacing, "Правила отступов плана не могут быть null");
        buildings = List.copyOf(Objects.requireNonNull(buildings, "Каталог плана не может быть null"));
        Set<String> ids = new HashSet<>();
        for (TownEntry entry : buildings) {
            if (!ids.add(entry.id())) {
                throw new IllegalArgumentException("Постройка '" + entry.id()
                        + "' в каталоге плана дважды: этап берёт постройку по id, двойник сделал бы выбор случайным");
            }
        }
    }

    public Optional<TownEntry> entry(String id) {
        return buildings.stream().filter(entry -> entry.id().equals(id)).findFirst();
    }

    public Optional<TownEntry> template(String placementId) {
        Objects.requireNonNull(placementId, "id места не может быть null: шаблон берут по нему");
        Optional<TownEntry> exact = entry(placementId);
        if (exact.isPresent() || !placementId.startsWith("orchard_")) {
            return exact;
        }
        return entry(OrchardSites.TEMPLATE);
    }

    public static TownRules parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, ROOT, origin, "town.json");
        try {
            JsonNode g = section(root, "ground", GROUND, origin);
            JsonNode s = section(root, "streets", STREETS, origin);
            JsonNode p = section(root, "spacing", SPACING, origin);
            List<TownEntry> entries = new ArrayList<>();
            for (JsonNode node : JsonResource.array(root.get("buildings"), origin, "построек плана")) {
                JsonResource.checkFields(node, ENTRY, origin, "постройка town.json");
                entries.add(new TownEntry(
                        JsonResource.text(node.get("id"), origin, "id"),
                        JsonResource.text(node.get("name"), origin, "name"),
                        JsonResource.integer(node.get("width"), origin, "width"),
                        JsonResource.integer(node.get("depth"), origin, "depth"),
                        TownEntry.Quarter.of(JsonResource.text(node.get("quarter"), origin, "quarter")),
                        flag(node, "level", origin),
                        flag(node, "rotate", origin),
                        flag(node, "field", origin)));
            }
            return new TownRules(
                    new Ground(
                            number(g, "area", origin),
                            number(g, "probeRadius", origin),
                            number(g, "cliffStep", origin),
                            number(g, "slopeWeight", origin),
                            number(g, "slopeMax", origin)),
                    new Streets(
                            number(s, "main", origin),
                            number(s, "side", origin),
                            number(s, "ring", origin),
                            number(s, "ringInset", origin),
                            number(s, "blockSize", origin)),
                    new Spacing(
                            number(p, "gap", origin),
                            number(p, "stageMargin", origin),
                            number(p, "chestFreePercent", origin),
                            number(p, "fenceStrip", origin)),
                    entries);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    public static TownRules current() {
        return SOURCE.current();
    }

    public static void useSource(Supplier<TownRules> supplier) {
        SOURCE.use(supplier);
    }

    public static TownRules load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    private static JsonNode section(JsonNode root, String name, Set<String> fields, String origin) {
        JsonNode node = root.get(name);
        if (node == null || !node.isObject()) {
            throw new IllegalStateException(
                    "Файл '" + origin + "': раздел '" + name + "' обязателен и задаётся объектом");
        }
        JsonResource.checkFields(node, fields, origin, "раздел " + name);
        return node;
    }

    private static int number(JsonNode node, String field, String origin) {
        return JsonResource.integer(node.get(field), origin, field);
    }

    private static boolean flag(JsonNode node, String field, String origin) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) {
            throw new IllegalStateException("Файл '" + origin + "': у постройки поле '" + field
                    + "' — true или false: выравнивание и поворот участка задаются явно");
        }
        return value.asBoolean();
    }
}
