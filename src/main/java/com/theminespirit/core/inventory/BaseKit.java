package com.theminespirit.core.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class BaseKit {
    private static final String RESOURCE_PATH = "data/theminespirit/inventory/base_kit.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ROOT_FIELDS = Set.of("rules");
    private static final Set<String> RULE_FIELDS = Set.of("match", "category", "keep", "mode");

    private final String origin;
    private final List<KitRule> rules;

    private BaseKit(String origin, List<KitRule> rules) {
        this.origin = origin;
        this.rules = List.copyOf(rules);
    }

    public static BaseKit load() {
        return load(RESOURCE_PATH);
    }

    public static BaseKit load(String resourcePath) {
        Objects.requireNonNull(resourcePath,
                "Путь к файлу базового набора не может быть null: по нему файл ищется на classpath");
        try (InputStream stream = BaseKit.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Файл базового набора '" + resourcePath
                        + "' не найден на classpath: проверьте, что он лежит в src/main/resources/data/theminespirit/inventory");
            }
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8), resourcePath);
        } catch (IOException error) {
            throw new UncheckedIOException("Файл базового набора '" + resourcePath
                    + "' не прочитан: проверьте права и кодировку файла", error);
        }
    }

    public static BaseKit parse(String json, String origin) {
        Objects.requireNonNull(json, "Текст файла базового набора не может быть null: разбирать нечего");
        Objects.requireNonNull(origin, "Источник файла не может быть null: сообщение об ошибке называет путь");
        JsonNode root = readTree(json, origin);
        if (!root.isObject()) {
            throw new IllegalStateException("Файл базового набора '" + origin
                    + "' — не объект: на верхнем уровне ждётся список rules");
        }
        checkFields(root, ROOT_FIELDS, origin, "файла базового набора");
        List<KitRule> rules = parseRules(root.get("rules"), origin);
        return new BaseKit(origin, rules);
    }

    public List<Slice> surplus(List<Stack> inventory, ValueTable values) {
        Grouping grouping = group(inventory, values);
        int[] keep = new int[grouping.stacks().size()];
        for (int index = 0; index < rules.size(); index++) {
            KitRule rule = rules.get(index);
            List<Integer> slots = grouping.byRule().get(index);
            if (slots.isEmpty()) {
                continue;
            }
            if (rule.mode() == KitMode.BEST) {
                keepBestOnly(grouping.stacks(), slots, rule.keep(), values, keep);
            } else {
                keepMostValuableFirst(grouping.stacks(), slots, rule.keep(), values, keep);
            }
        }
        List<Slice> surplus = new ArrayList<>();
        for (int slot = 0; slot < grouping.stacks().size(); slot++) {
            Stack stack = grouping.stacks().get(slot);
            if (stack == null) {
                continue;
            }
            int give = stack.count() - keep[slot];
            if (give > 0) {
                surplus.add(new Slice(slot, give));
            }
        }
        return surplus;
    }

    public boolean isSatisfiedBy(List<Stack> inventory, ValueTable values) {
        Grouping grouping = group(inventory, values);
        for (int slot = 0; slot < grouping.stacks().size(); slot++) {
            if (grouping.claimed()[slot] < 0 && grouping.stacks().get(slot) != null) {
                return false;
            }
        }
        for (int index = 0; index < rules.size(); index++) {
            KitRule rule = rules.get(index);
            List<Integer> slots = grouping.byRule().get(index);
            if (slots.isEmpty()) {
                continue;
            }
            int total = 0;
            for (int slot : slots) {
                total += grouping.stacks().get(slot).count();
            }
            if (total > rule.keep()) {
                return false;
            }
            if (rule.mode() == KitMode.BEST) {
                String bestItem = grouping.stacks().get(bestSlot(grouping.stacks(), slots, values)).item();
                for (int slot : slots) {
                    if (!grouping.stacks().get(slot).item().equals(bestItem)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public String origin() {
        return origin;
    }

    public List<KitRule> rules() {
        return rules;
    }

    private static void keepBestOnly(List<Stack> stacks, List<Integer> slots, int keep, ValueTable values,
            int[] result) {
        int best = bestSlot(stacks, slots, values);
        result[best] = Math.min(stacks.get(best).count(), keep);
    }

    private static void keepMostValuableFirst(List<Stack> stacks, List<Integer> slots, int keep, ValueTable values,
            int[] result) {
        List<Integer> order = new ArrayList<>(slots);
        order.sort((left, right) -> {
            int byValue = Integer.compare(values.stackValue(stacks.get(right)), values.stackValue(stacks.get(left)));
            return byValue != 0 ? byValue : Integer.compare(left, right);
        });
        int left = keep;
        for (int slot : order) {
            if (left <= 0) {
                break;
            }
            int take = Math.min(stacks.get(slot).count(), left);
            result[slot] = take;
            left -= take;
        }
    }

    private static int bestSlot(List<Stack> stacks, List<Integer> slots, ValueTable values) {
        int best = slots.get(0);
        for (int slot : slots) {
            int byValue = Integer.compare(values.valueOf(stacks.get(slot).item()),
                    values.valueOf(stacks.get(best).item()));
            if (byValue > 0 || (byValue == 0 && slot < best)) {
                best = slot;
            }
        }
        return best;
    }

    private Grouping group(List<Stack> inventory, ValueTable values) {
        Objects.requireNonNull(inventory, "Инвентарь не может быть null: излишек считают по слотам инвентаря");
        Objects.requireNonNull(values, "Таблица ценностей не может быть null: "
                + "по ней набор отличает лучшую кирку от худшей");
        List<Stack> stacks = new ArrayList<>(inventory.size());
        List<List<Integer>> byRule = new ArrayList<>(rules.size());
        for (int index = 0; index < rules.size(); index++) {
            byRule.add(new ArrayList<>());
        }
        int[] claimed = new int[inventory.size()];
        for (int slot = 0; slot < inventory.size(); slot++) {
            Stack stack = inventory.get(slot);
            stacks.add(stack);
            if (stack == null) {
                claimed[slot] = -1;
                continue;
            }
            int rule = firstRule(stack.item(), values);
            claimed[slot] = rule;
            if (rule >= 0) {
                byRule.get(rule).add(slot);
            }
        }
        return new Grouping(stacks, byRule, claimed);
    }

    private int firstRule(String item, ValueTable values) {
        for (int index = 0; index < rules.size(); index++) {
            if (rules.get(index).covers(item, values)) {
                return index;
            }
        }
        return -1;
    }

    private record Grouping(List<Stack> stacks, List<List<Integer>> byRule, int[] claimed) {
    }

    private static JsonNode readTree(String json, String origin) {
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root == null) {
                throw new IllegalStateException("Файл базового набора '" + origin + "' пуст: в нём нет ни одного правила");
            }
            return root;
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Файл базового набора '" + origin + "' — не разобран JSON: "
                    + error.getOriginalMessage() + ". Исправьте синтаксис файла");
        }
    }

    private static List<KitRule> parseRules(JsonNode node, String origin) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new IllegalStateException("Файл базового набора '" + origin
                    + "' без списка rules: без правил набор пуст и разгрузка отдала бы всё подряд");
        }
        List<KitRule> rules = new ArrayList<>(node.size());
        for (int index = 0; index < node.size(); index++) {
            JsonNode ruleNode = node.get(index);
            if (!ruleNode.isObject()) {
                throw new IllegalStateException("Файл базового набора '" + origin + "': правило №" + index
                        + " — не объект с полями match/category, keep и mode");
            }
            checkFields(ruleNode, RULE_FIELDS, origin, "правила №" + index);
            rules.add(rule(stringsField(ruleNode.get("match"), origin, "правила №" + index),
                    textField(ruleNode.get("category"), origin, "правила №" + index),
                    integerField(ruleNode.get("keep"), origin, "правила №" + index),
                    modeField(ruleNode.get("mode"), origin, "правила №" + index), origin, "правила №" + index));
        }
        return rules;
    }

    private static KitRule rule(List<String> match, String category, int keep, KitMode mode, String origin,
            String what) {
        try {
            return new KitRule(match, category, keep, mode);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл базового набора '" + origin + "': " + what + " неверно — "
                    + error.getMessage(), error);
        }
    }

    private static KitMode modeField(JsonNode node, String origin, String what) {
        if (node == null || !node.isTextual()) {
            throw new IllegalStateException("Файл базового набора '" + origin + "': у " + what
                    + " нет режима mode: режим best оставляет лучший предмет набора, any — любое количество");
        }
        try {
            return KitMode.valueOf(node.asText().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл базового набора '" + origin + "': у " + what + " режим '"
                    + node.asText() + "' неизвестен: допустимы best и any", error);
        }
    }

    private static List<String> stringsField(JsonNode node, String origin, String what) {
        if (node == null) {
            return null;
        }
        if (!node.isArray() || node.isEmpty()) {
            throw new IllegalStateException("Файл базового набора '" + origin + "': у " + what
                    + " поле match пусто или не список: без шаблонов правило не описывает предметы");
        }
        List<String> patterns = new ArrayList<>(node.size());
        for (int index = 0; index < node.size(); index++) {
            JsonNode pattern = node.get(index);
            if (!pattern.isTextual()) {
                throw new IllegalStateException("Файл базового набора '" + origin + "': шаблон №" + index + " у "
                        + what + " — не строка: шаблон это текст вроде \"*_pickaxe\"");
            }
            patterns.add(pattern.asText());
        }
        return patterns;
    }

    private static String textField(JsonNode node, String origin, String what) {
        if (node == null) {
            return null;
        }
        if (!node.isTextual() || node.asText().isBlank()) {
            throw new IllegalStateException("Файл базового набора '" + origin + "': у " + what
                    + " поле category — не строка: имя категории берётся из файла ценностей");
        }
        return node.asText();
    }

    private static int integerField(JsonNode node, String origin, String what) {
        if (node == null || !node.isInt()) {
            throw new IllegalStateException("Файл базового набора '" + origin + "': у " + what
                    + " нет квоты keep или она не целая: квота это сколько предметов набора остаётся у духа");
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
                throw new IllegalStateException("Файл базового набора '" + origin + "': неизвестное поле '" + field
                        + "' у " + what + ": допустимы " + allowed + ", опечатка в имени поля сделала бы квоту мёртвой");
            }
        }
    }
}
