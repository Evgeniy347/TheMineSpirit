package com.theminespirit.core.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.api.JsonResource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

final class KitJson {
    private KitJson() {}

    static List<KitRule> parseRules(JsonNode node, String origin) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new IllegalStateException("Файл базового набора '" + origin
                    + "' без списка rules: без правил набор пуст и разгрузка отдала бы всё подряд");
        }
        List<JsonNode> nodes = JsonResource.items(node);
        List<KitRule> rules = new ArrayList<>(nodes.size());
        for (int index = 0; index < nodes.size(); index++) {
            JsonNode ruleNode = nodes.get(index);
            if (!ruleNode.isObject()) {
                throw new IllegalStateException("Файл базового набора '" + origin + "': правило №" + index
                        + " — не объект с полями match/category, keep и mode");
            }
            JsonResource.checkFields(ruleNode, BaseKit.RULE_FIELDS, origin, "правила №" + index);
            rules.add(rule(
                    optionalStringsField(ruleNode.get("match"), origin, "поля match правила №" + index),
                    textField(ruleNode.get("category"), origin, "правила №" + index),
                    keepField(ruleNode.get("keep"), origin, "поля keep правила №" + index),
                    modeField(ruleNode.get("mode"), origin, "правила №" + index),
                    origin,
                    "правила №" + index));
        }
        return rules;
    }

    static KitRule rule(
            @Nullable List<String> match,
            @Nullable String category,
            int keep,
            KitMode mode,
            String origin,
            String what) {
        try {
            return new KitRule(match, category, keep, mode);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException(
                    "Файл базового набора '" + origin + "': " + what + " неверно — " + error.getMessage(), error);
        }
    }

    static KitMode modeField(JsonNode node, String origin, String what) {
        if (node == null || !node.isTextual()) {
            throw new IllegalStateException("Файл базового набора '" + origin + "': у " + what
                    + " нет режима mode: режим best оставляет лучший предмет набора, any — любое количество");
        }
        try {
            return KitMode.valueOf(node.asText().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException(
                    "Файл базового набора '" + origin + "': у " + what + " режим '" + node.asText()
                            + "' неизвестен: допустимы best и any",
                    error);
        }
    }

    static @Nullable List<String> optionalStringsField(@Nullable JsonNode node, String origin, String what) {
        if (node == null || node.isNull()) {
            return null;
        }
        return JsonResource.strings(node, origin, what);
    }

    static @Nullable String textField(@Nullable JsonNode node, String origin, String what) {
        if (node == null) {
            return null;
        }
        if (!node.isTextual() || node.asText().isBlank()) {
            throw new IllegalStateException("Файл базового набора '" + origin + "': у " + what
                    + " поле category — не строка: имя категории берётся из файла ценностей, "
                    + "по нему решается, какие предметы в одну группу");
        }
        return node.asText();
    }

    static int keepField(JsonNode node, String origin, String what) {
        if (node == null || !node.isInt()) {
            throw new IllegalStateException("Файл базового набора '" + origin + "': у " + what
                    + " нет квоты keep или она не целая: квота это сколько предметов набора остаётся "
                    + "у духа, без неё непонятно, что разгружать");
        }
        return node.asInt();
    }
}
