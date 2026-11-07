package com.theminespirit.core.intellect;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.inventory.ValueTable;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.reload.TableSlot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public record OutsideRules(int partyMin, List<Need> mineKit) {
    public static final String RESOURCE = "data/theminespirit/intellect/outside.json";
    public static final int MIN_PARTY = 2;

    private static final TableSlot<OutsideRules> SOURCE = new TableSlot<>(OutsideRules::load);
    private static final String MINE_KIT = "mineKit";
    private static final Set<String> FIELDS = Set.of("partyMin", MINE_KIT);
    private static final Set<String> NEED_FIELDS = Set.of("match", "category", "count");
    private static final OutsideRules DEFAULTS = new OutsideRules(
            MIN_PARTY,
            List.of(
                    new Need(List.of("*_pickaxe"), null, 1),
                    new Need(List.of("torch"), null, 8),
                    new Need(null, "food", 3)));

    public OutsideRules {
        if (partyMin < MIN_PARTY) {
            throw new IllegalArgumentException("Порог группы " + partyMin + " меньше " + MIN_PARTY
                    + ": в одиночку за границу поселения не выходят, порог ниже двух отменяет правило");
        }
        Objects.requireNonNull(mineKit, "Набор шахты не может быть null: пустой набор задают пустым списком");
        mineKit = List.copyOf(mineKit);
    }

    public static OutsideRules defaults() {
        return DEFAULTS;
    }

    public static OutsideRules loadDefault() {
        return SOURCE.current();
    }

    public static void useSource(Supplier<OutsideRules> supplier) {
        SOURCE.use(supplier);
    }

    public static OutsideRules load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    public static OutsideRules parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "outside.json");
        int partyMin = JsonResource.integer(root.get("partyMin"), origin, "partyMin");
        List<Need> kit = parseKit(root.get(MINE_KIT), origin);
        try {
            return new OutsideRules(partyMin, kit);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    private static List<Need> parseKit(JsonNode node, String origin) {
        List<JsonNode> items = JsonResource.array(node, origin, MINE_KIT);
        List<Need> kit = new ArrayList<>(items.size());
        for (JsonNode item : items) {
            if (!item.isObject()) {
                throw new IllegalStateException(
                        "Файл '" + origin + "': у mineKit элемент не объект с полями match, category, count");
            }
            JsonResource.checkFields(item, NEED_FIELDS, origin, MINE_KIT);
            List<String> match =
                    item.has("match") ? JsonResource.strings(item.get("match"), origin, "match набора шахты") : null;
            String category = item.has("category")
                    ? JsonResource.text(item.get("category"), origin, "category набора шахты")
                    : null;
            int count = JsonResource.integer(item.get("count"), origin, "count набора шахты");
            try {
                kit.add(new Need(match, category, count));
            } catch (IllegalArgumentException error) {
                throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
            }
        }
        return List.copyOf(kit);
    }

    public record Need(
            @Nullable List<String> match, @Nullable String category, int count) {
        public Need {
            if ((match == null) == (category == null)) {
                throw new IllegalArgumentException("У нужды набора задан не ровно один из match и category: "
                        + "два способа выбора или ни одного делают требование неопределённым");
            }
            if (match != null) {
                if (match.isEmpty()) {
                    throw new IllegalArgumentException(
                            "У нужды набора пустой match: " + "без шаблонов нужда не описывает ни один предмет");
                }
                IdFilter.of(match);
                match = List.copyOf(match);
            }
            if (category != null && category.isBlank()) {
                throw new IllegalArgumentException(
                        "Категория нужды набора пуста: " + "имя категории берётся из файла ценностей");
            }
            if (count < 1) {
                throw new IllegalArgumentException("Количество нужды набора " + count
                        + ": требуют хотя бы один предмет, нулевая нужда бессмысленна");
            }
        }

        public boolean covers(String item, ValueTable values) {
            Objects.requireNonNull(item, "Предмет не может быть null: нужда проверяет предмет по id");
            Objects.requireNonNull(values, "Таблица ценностей не может быть null: по ней нужда находит категорию");
            if (category != null) {
                return values.categoryOf(item).equals(category);
            }
            List<String> patterns = match;
            if (patterns == null) {
                throw new IllegalStateException("Нужда набора без match и без category: конструктор требует ровно "
                        + "один способ выбора предмета");
            }
            return IdFilter.of(patterns).matches(item);
        }

        public String describe() {
            return category != null ? category : String.join(",", match);
        }
    }
}
