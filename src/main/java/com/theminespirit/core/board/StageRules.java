package com.theminespirit.core.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record StageRules(List<Stage> stages, Map<BoardCase, List<String>> schemes) {
    public static final String RESOURCE = "data/theminespirit/board/stages.json";
    private static final Set<String> ROOT_FIELDS = Set.of("stages", "schemes");
    private static final Set<String> STAGE_FIELDS = Set.of("stage", "buildings", "plan");

    public record Stage(int stage, Map<BoardCase, Integer> buildings, List<String> plan) {
        public Stage(int stage, Map<BoardCase, Integer> buildings) {
            this(stage, buildings, List.of());
        }

        public Stage {
            buildings = Map.copyOf(Objects.requireNonNull(
                    buildings, "Состав этапа не может быть null: этап без построек задают пустой картой"));
            plan = List.copyOf(Objects.requireNonNull(
                    plan, "Набор этапа в плане не может быть null: этап без мест задают пустым списком"));
            for (Map.Entry<BoardCase, Integer> entry : buildings.entrySet()) {
                if (entry.getValue() < 1) {
                    throw new IllegalArgumentException("Этап " + stage + ": уровень " + entry.getKey() + " = "
                            + entry.getValue() + ", а журнал доски отмечает уровни с первого");
                }
            }
        }
    }

    public StageRules(List<Stage> stages) {
        this(stages, Map.of());
    }

    public StageRules {
        stages = List.copyOf(Objects.requireNonNull(stages, "Этапы не могут быть null"));
        Map<BoardCase, List<String>> copied = new LinkedHashMap<>();
        Objects.requireNonNull(schemes, "Схемы уровней не могут быть null: без схем задают пустую карту")
                .forEach((boardCase, files) -> copied.put(boardCase, List.copyOf(files)));
        schemes = Map.copyOf(copied);
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("Этапов нет: поселение начинается с этапа 1, его состав обязателен");
        }
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).stage() != i + 1) {
                throw new IllegalArgumentException("Этап на месте " + (i + 1) + " назван "
                        + stages.get(i).stage() + ": этапы идут подряд с первого, иначе переход перепрыгнет этап");
            }
        }
    }

    public List<List<String>> plans() {
        return stages.stream().map(Stage::plan).toList();
    }

    public int stageOf(BoardLedger ledger) {
        Objects.requireNonNull(ledger, "Журнал доски не может быть null: этап читается по построенному");
        for (Stage stage : stages) {
            if (!missing(stage, ledger).isEmpty()) {
                return stage.stage();
            }
        }
        return stages.size() + 1;
    }

    public Optional<String> scheme(BoardCase boardCase, int level) {
        Objects.requireNonNull(boardCase, "Кейс постройки не может быть null: без него неизвестна схема уровня");
        List<String> files = schemes.getOrDefault(boardCase, List.of());
        return level >= 1 && level <= files.size() ? Optional.of(files.get(level - 1)) : Optional.empty();
    }

    public Map<BoardCase, Integer> missingOf(BoardLedger ledger) {
        int current = stageOf(ledger);
        return current > stages.size() ? Map.of() : missing(stages.get(current - 1), ledger);
    }

    private static Map<BoardCase, Integer> missing(Stage stage, BoardLedger ledger) {
        Map<BoardCase, Integer> missing = new LinkedHashMap<>();
        stage.buildings().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(entry -> !ledger.has(entry.getKey(), entry.getValue()))
                .forEach(entry -> missing.put(entry.getKey(), entry.getValue()));
        return missing;
    }

    public static StageRules parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, ROOT_FIELDS, origin, "stages.json");
        List<Stage> stages = new ArrayList<>();
        try {
            for (JsonNode node : JsonResource.array(root.get("stages"), origin, "этапов")) {
                JsonResource.checkFields(node, STAGE_FIELDS, origin, "этап stages.json");
                stages.add(new Stage(
                        JsonResource.integer(node.get("stage"), origin, "stage"),
                        buildings(node.get("buildings"), origin),
                        JsonResource.optionalStrings(node.get("plan"), origin, "набора этапа в плане")));
            }
            return new StageRules(stages, schemes(root.get("schemes"), origin));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    public static StageRules load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
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
}
