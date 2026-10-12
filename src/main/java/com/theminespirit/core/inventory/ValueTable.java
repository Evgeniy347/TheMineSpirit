package com.theminespirit.core.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.observation.IdFilter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ValueTable {
    public static final int VALUE_LIMIT = 100_000;

    private static final String RESOURCE_PATH = "data/theminespirit/inventory/item_values.json";
    private static final Set<String> ROOT_FIELDS = Set.of("rules", "default", "categories");
    private static final Set<String> RULE_FIELDS = Set.of("match", "value");
    private static final Set<String> CATEGORY_FIELDS = Set.of("name", "match");

    private final String origin;
    private final List<ValueRule> rules;
    private final List<Category> categories;
    private final int defaultValue;
    private final Map<String, Integer> valueCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> rankCache = new ConcurrentHashMap<>();
    private final AtomicLong categoryLookups = new AtomicLong();

    private ValueTable(String origin, List<ValueRule> rules, List<Category> categories, int defaultValue) {
        this.origin = origin;
        this.rules = List.copyOf(rules);
        this.categories = List.copyOf(categories);
        this.defaultValue = defaultValue;
    }

    public static ValueTable load() {
        return load(RESOURCE_PATH);
    }

    public static ValueTable load(String resourcePath) {
        return parse(JsonResource.readClasspath(resourcePath), resourcePath);
    }

    public static ValueTable parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, ROOT_FIELDS, origin, "файла ценностей");
        List<ValueRule> rules = parseRules(root.get("rules"), origin);
        List<Category> categories = parseCategories(root.get("categories"), origin);
        int defaultValue = parseDefault(root.get("default"), origin);
        checkNoDeadRule(rules, origin);
        return new ValueTable(origin, rules, categories, defaultValue);
    }

    public int valueOf(String item) {
        Objects.requireNonNull(item, "Предмет не назван: ценность считается по id");
        return valueCache.computeIfAbsent(item, this::matchRuleValue);
    }

    public int categoryRank(String item) {
        Objects.requireNonNull(item, "Предмет не назван: категория определяется по id");
        categoryLookups.incrementAndGet();
        return rankCache.computeIfAbsent(item, this::matchCategoryRank);
    }

    public long categoryLookups() {
        return categoryLookups.get();
    }

    private int matchRuleValue(String item) {
        for (ValueRule rule : rules) {
            if (rule.covers(item)) {
                return rule.value();
            }
        }
        return defaultValue;
    }

    private int matchCategoryRank(String item) {
        for (int index = 0; index < categories.size(); index++) {
            if (categories.get(index).covers(item)) {
                return index;
            }
        }
        throw new IllegalStateException("Предмет '" + item + "' не попал ни в одну категорию файла '" + origin
                + "': последняя категория обязана содержать шаблон '*', проверьте файл ценностей");
    }

    public int stackValue(Stack stack) {
        Objects.requireNonNull(stack, "Стопка не может быть null: ценность считается по стопке");
        return valueOf(stack.item()) * stack.count();
    }

    public String categoryOf(String item) {
        return categories.get(categoryRank(item)).name();
    }

    public String origin() {
        return origin;
    }

    public List<ValueRule> rules() {
        return rules;
    }

    public List<Category> categories() {
        return categories;
    }

    public int defaultValue() {
        return defaultValue;
    }

    private static List<ValueRule> parseRules(JsonNode node, String origin) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new IllegalStateException("Файл ценностей '" + origin
                    + "' без списка rules: без правил все предметы получили бы одно значение default");
        }
        List<JsonNode> nodes = JsonResource.array(node, origin, "списка rules");
        List<ValueRule> rules = new ArrayList<>(nodes.size());
        for (int index = 0; index < nodes.size(); index++) {
            JsonNode ruleNode = nodes.get(index);
            if (!ruleNode.isObject()) {
                throw new IllegalStateException("Файл ценностей '" + origin + "': правило №" + index
                        + " — не объект с полями match и value");
            }
            JsonResource.checkFields(ruleNode, RULE_FIELDS, origin, "правила №" + index);
            int value = JsonResource.integer(ruleNode.get("value"), origin, "поля value правила №" + index);
            if (value < 0) {
                throw new IllegalStateException("Файл ценностей '" + origin + "': ценность правила №" + index
                        + " равна " + value + ": отрицательная ценность правила делает выбор «самое дешёвое» бессмысленным");
            }
            rules.add(rule(JsonResource.strings(ruleNode.get("match"), origin, "поля match правила №" + index),
                    value, origin, "правила №" + index));
        }
        return rules;
    }

    private static List<Category> parseCategories(JsonNode node, String origin) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new IllegalStateException("Файл ценностей '" + origin
                    + "' без списка categories: сортёру сундука не по чем расставить группы предметов");
        }
        List<JsonNode> nodes = JsonResource.array(node, origin, "списка categories");
        List<Category> categories = new ArrayList<>(nodes.size());
        Set<String> names = new LinkedHashSet<>();
        for (int index = 0; index < nodes.size(); index++) {
            JsonNode categoryNode = nodes.get(index);
            if (!categoryNode.isObject()) {
                throw new IllegalStateException("Файл ценностей '" + origin + "': категория №" + index
                        + " — не объект с полями name и match");
            }
            JsonResource.checkFields(categoryNode, CATEGORY_FIELDS, origin, "категории №" + index);
            String name = JsonResource.text(categoryNode.get("name"), origin, "поля name категории №" + index);
            categories.add(category(name,
                    JsonResource.strings(categoryNode.get("match"), origin, "поля match категории №" + index),
                    origin, "категории №" + index));
            names.add(name);
        }
        if (names.size() != categories.size()) {
            throw new IllegalStateException("Файл ценностей '" + origin
                    + "': имена категорий повторяются: порядок категорий задаёт раскладку сундука, "
                    + "повтор делает его неопределённым");
        }
        Category last = categories.get(categories.size() - 1);
        if (!last.match().contains("*")) {
            throw new IllegalStateException("Файл ценностей '" + origin + "': последняя категория '" + last.name()
                    + "' не содержит шаблон '*': предмет вне категорий не получит места при сортировке сундука");
        }
        return categories;
    }

    private static int parseDefault(JsonNode node, String origin) {
        int value = JsonResource.integer(node, origin, "поля default");
        if (value < 0) {
            throw new IllegalStateException("Файл ценностей '" + origin + "': default равен " + value
                    + ": отрицательная ценность правила делает выбор «самое дешёвое» бессмысленным");
        }
        return value;
    }

    private static ValueRule rule(List<String> match, int value, String origin, String what) {
        try {
            return new ValueRule(match, value);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл ценностей '" + origin + "': " + what + " неверно — "
                    + error.getMessage(), error);
        }
    }

    private static Category category(String name, List<String> match, String origin, String what) {
        try {
            return new Category(name, match);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл ценностей '" + origin + "': " + what + " неверна — "
                    + error.getMessage(), error);
        }
    }

    private static void checkNoDeadRule(List<ValueRule> rules, String origin) {
        for (int index = 0; index < rules.size(); index++) {
            if (coveredByEarlier(rules, index)) {
                throw new IllegalStateException("Файл ценностей '" + origin + "': правило №" + index + " "
                        + rules.get(index).match() + " перекрыто предыдущими правилами целиком и никогда не сработает: "
                        + "уберите правило или переставьте его выше");
            }
        }
    }

    private static boolean coveredByEarlier(List<ValueRule> rules, int index) {
        for (String pattern : rules.get(index).match()) {
            if (!coveredByEarlier(rules, index, pattern)) {
                return false;
            }
        }
        return true;
    }

    private static boolean coveredByEarlier(List<ValueRule> rules, int index, String pattern) {
        for (int earlier = 0; earlier < index; earlier++) {
            for (String wide : rules.get(earlier).match()) {
                if (subsumes(wide, pattern)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean subsumes(String wide, String narrow) {
        if (wide.equals(narrow) || wide.equals("*")) {
            return true;
        }
        return narrow.indexOf('*') < 0 && IdFilter.of(List.of(wide)).matches(narrow);
    }
}
