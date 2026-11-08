package com.theminespirit.core.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class StageRulesJson {
    private static final Set<String> ROOT_FIELDS = Set.of("stages", "schemes", "planCases");
    private static final Set<String> STAGE_FIELDS = Set.of("stage", "buildings", "plan", "requires", "optional");

    private StageRulesJson() {}

    static StageRules parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, ROOT_FIELDS, origin, "stages.json");
        List<StageRules.Stage> stages = new ArrayList<>();
        try {
            for (JsonNode node : JsonResource.array(root.get("stages"), origin, "этапов")) {
                JsonResource.checkFields(node, STAGE_FIELDS, origin, "этап stages.json");
                stages.add(new StageRules.Stage(
                        JsonResource.integer(node.get("stage"), origin, "stage"),
                        buildings(node.get("buildings"), origin),
                        JsonResource.optionalStrings(node.get("plan"), origin, "набора этапа в плане"),
                        requires(node.get("requires"), origin),
                        optional(node.get("optional"), origin)));
            }
            return new StageRules(
                    stages, schemes(root.get("schemes"), origin), planCases(root.get("planCases"), origin));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    private static Map<BoardCase, List<String>> schemes(JsonNode node, String origin) {
        if (node == null) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw new IllegalStateException("Файл '" + origin + "': поле 'schemes' — не объект «кейс → схемы уровней»");
        }
        Map<BoardCase, List<String>> schemes = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            List<String> files = new ArrayList<>();
            for (JsonNode file : JsonResource.array(field.getValue(), origin, "схем " + field.getKey())) {
                if (!file.isTextual() || file.asText().isBlank()) {
                    throw new IllegalStateException("Файл '" + origin + "': схема уровня " + field.getKey()
                            + " — не имя файла схемы, а " + file);
                }
                files.add(file.asText());
            }
            schemes.put(boardCase(field.getKey(), origin), files);
        }
        return schemes;
    }

    private static BoardCase boardCase(String name, String origin) {
        try {
            return BoardCase.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalStateException(
                    "Файл '" + origin + "': неизвестный кейс постройки '" + name + "'", unknown);
        }
    }

    private static Map<BoardCase, Integer> buildings(JsonNode node, String origin) {
        if (node == null || !node.isObject()) {
            throw new IllegalStateException("Файл '" + origin + "': поле 'buildings' — не объект «кейс → уровень»");
        }
        Map<BoardCase, Integer> buildings = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            buildings.put(
                    boardCase(field.getKey(), origin), JsonResource.integer(field.getValue(), origin, field.getKey()));
        }
        return buildings;
    }

    private static Map<String, Integer> requires(JsonNode node, String origin) {
        return planLevels(node, origin, "requires", "объект «постройка плана → уровень»");
    }

    private static Map<String, Integer> optional(JsonNode node, String origin) {
        return planLevels(node, origin, "optional", "объект «желательная постройка плана → уровень»");
    }

    private static Map<String, Integer> planLevels(JsonNode node, String origin, String field, String shape) {
        if (node == null) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw new IllegalStateException("Файл '" + origin + "': поле '" + field + "' — не " + shape);
        }
        Map<String, Integer> levels = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            levels.put(entry.getKey(), JsonResource.integer(entry.getValue(), origin, entry.getKey()));
        }
        return levels;
    }

    private static Map<String, BoardCase> planCases(JsonNode node, String origin) {
        if (node == null) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw new IllegalStateException(
                    "Файл '" + origin + "': поле 'planCases' — не объект «постройка плана → кейс доски»");
        }
        Map<String, BoardCase> cases = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            cases.put(
                    field.getKey(),
                    boardCase(JsonResource.text(field.getValue(), origin, "кейса " + field.getKey()), origin));
        }
        return cases;
    }
}
