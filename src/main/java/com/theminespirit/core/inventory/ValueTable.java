package com.theminespirit.core.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.IdFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ValueTable {
    public static final int VALUE_LIMIT = 100_000;

    private static final String RESOURCE_PATH = "data/theminespirit/inventory/item_values.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ROOT_FIELDS = Set.of("rules", "default", "categories");
    private static final Set<String> RULE_FIELDS = Set.of("match", "value");
    private static final Set<String> CATEGORY_FIELDS = Set.of("name", "match");

    private final String origin;
    private final List<ValueRule> rules;
    private final List<Category> categories;
    private final int defaultValue;

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
        Objects.requireNonNull(resourcePath, "Путь к файлу ценностей не может быть null: по нему файл ищется на classpath");
        try (InputStream stream = ValueTable.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Файл ценностей '" + resourcePath
                        + "' не найден на classpath: проверьте, что он лежит в src/main/resources/data/theminespirit/inventory");
            }
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8), resourcePath);
        } catch (IOException error) {
            throw new UncheckedIOException("Файл ценностей '" + resourcePath
                    + "' не прочитан: проверьте права и кодировку файла", error);
        }
    }

    public static ValueTable parse(String json, String origin) {
        Objects.requireNonNull(json, "Текст файла ценностей не может быть null: разбирать нечего");
        Objects.requireNonNull(origin, "Источник файла ценностей не может быть null: сообщение об ошибке называет путь");
        JsonNode root = readTree(json, origin);
        if (!root.isObject()) {
            throw new IllegalStateException("Файл ценностей '" + origin
                    + "' — не объект: на верхнем уровне ждутся поля rules, default и categories");
        }
        checkFields(root, ROOT_FIELDS, origin, "файла ценностей");
        List<ValueRule> rules = parseRules(root.get("rules"), origin);
        List<Category> categories = parseCategories(root.get("categories"), origin);
        int defaultValue = parseDefault(root.get("default"), origin);
        checkNoDeadRule(rules, origin);
        return new ValueTable(origin, rules, categories, defaultValue);
    }

    public int valueOf(String item) {
        Objects.requireNonNull(item, "Предмет не назван: ценность считается по id");
        for (ValueRule rule : rules) {
            if (rule.covers(item)) {
                return rule.value();
            }
        }
        return defaultValue;
    }

    public int stackValue(Stack stack) {
        Objects.requireNonNull(stack, "Стопка не может быть null: ценность считается по стопке");
        return valueOf(stack.item()) * stack.count();
    }

    public String categoryOf(String item) {
        Objects.requireNonNull(item, "Предмет не назван: категория определяется по id");
        for (Category category : categories) {
            if (category.covers(item)) {
                return category.name();
            }
        }
        throw new IllegalStateException("Предмет '" + item + "' не попал ни в одну категорию файла '" + origin
                + "': последняя категория обязана содержать шаблон '*', проверьте файл ценностей");
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

    private static JsonNode readTree(String json, String origin) {
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root == null) {
                throw new IllegalStateException("Файл ценностей '" + origin + "' пуст: в нём нет ни одного правила");
            }
            return root;
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Файл ценностей '" + origin + "' — не разобран JSON: "
                    + error.getOriginalMessage() + ". Исправьте синтаксис файла");
        }
    }

    private static List<ValueRule> parseRules(JsonNode node, String origin) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new IllegalStateException("Файл ценностей '" + origin
                    + "' без списка rules: без правил все предметы получили бы одно значение default");
        }
        List<ValueRule> rules = new ArrayList<>(node.size());
        for (int index = 0; index < node.size(); index++) {
            JsonNode ruleNode = node.get(index);
            if (!ruleNode.isObject()) {
                throw new IllegalStateException("Файл ценностей '" + origin + "': правило №" + index
                        + " — не объект с полями match и value");
            }
            checkFields(ruleNode, RULE_FIELDS, origin, "правила №" + index);
            int value = integerField(ruleNode.get("value"), origin, "правила №" + index);
            if (value < 0) {
                throw new IllegalStateException("Файл ценностей '" + origin + "': ценность правила №" + index
                        + " равна " + value + ": отрицательная ценность правила делает выбор «самое дешёвое» бессмысленным");
            }
            rules.add(rule(stringsField(ruleNode.get("match"), origin, "правила №" + index), value, origin,
                    "правила №" + index));
        }
        return rules;
    }

    private static List<Category> parseCategories(JsonNode node, String origin) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new IllegalStateException("Файл ценностей '" + origin
                    + "' без списка categories: сортёру сундука не по чем расставить группы предметов");
        }
        List<Category> categories = new ArrayList<>(node.size());
        Set<String> names = new LinkedHashSet<>();
        for (int index = 0; index < node.size(); index++) {
            JsonNode categoryNode = node.get(index);
            if (!categoryNode.isObject()) {
                throw new IllegalStateException("Файл ценностей '" + origin + "': категория №" + index
                        + " — не объект с полями name и match");
            }
            checkFields(categoryNode, CATEGORY_FIELDS, origin, "категории №" + index);
            String name = categoryNode.get("name").isTextual() ? categoryNode.get("name").asText() : "";
            categories.add(category(name, stringsField(categoryNode.get("match"), origin, "категории №" + index),
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
        int value = integerField(node, origin, "значения default");
        if (value < 0) {
            throw new IllegalStateException("Файл ценностей '" + origin + "': default равен " + value
                    + ": отрицательная ценность правила делает выбор «самое дешёвое» бессмысленным");
        }
        return value;
    }

    private static List<String> stringsField(JsonNode node, String origin, String what) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new IllegalStateException("Файл ценностей '" + origin + "': у " + what
                    + " поле match пусто или не список: без шаблонов правило не описывает предметы");
        }
        List<String> patterns = new ArrayList<>(node.size());
        for (int index = 0; index < node.size(); index++) {
            JsonNode pattern = node.get(index);
            if (!pattern.isTextual()) {
                throw new IllegalStateException("Файл ценностей '" + origin + "': шаблон №" + index + " у " + what
                        + " — не строка: шаблон это текст вроде \"*_ingot\"");
            }
            patterns.add(pattern.asText());
        }
        return patterns;
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

    private static int integerField(JsonNode node, String origin, String what) {        if (node == null || !node.isInt()) {
            throw new IllegalStateException("Файл ценностей '" + origin + "': у " + what
                    + " значение целое: ценность считается как целое число предметов");
        }
        return node.asInt();
    }

    private static void checkFields(JsonNode node, Set<String> allowed, String origin, String what) {
        List<String> fields = new ArrayList<>();
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            fields.add(names.next());
        }
        for (String field : fields) {
            if (!allowed.contains(field)) {
                throw new IllegalStateException("Файл ценности '" + origin + "': неизвестное поле '" + field + "' у "
                        + what + ": допустимы " + allowed + ", опечатка в имени поля делает правило мёртвым");
            }
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
