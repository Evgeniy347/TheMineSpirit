package com.theminespirit.core.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Этапы развития поселения: этап N — набор обязательных построек «кейс → уровень» по журналу
 * доски. Когда все постройки этапа N стоят, поселение переходит на этап N+1; постройка уровня L ставится только
 * с этапа L. Текущий этап — первый, чей состав не построен; всё построено — этап за последним. Состав — горячая таблица {@code board/stages.json}, а не решение модели (plan.md «Рост по этапам»).
 */
public record StageRules(List<Stage> stages) {
    public static final String RESOURCE = "data/theminespirit/board/stages.json";
    private static final Set<String> ROOT_FIELDS = Set.of("stages");
    private static final Set<String> STAGE_FIELDS = Set.of("stage", "buildings");

    public record Stage(int stage, Map<BoardCase, Integer> buildings) {
        public Stage {
            buildings = Map.copyOf(Objects.requireNonNull(
                    buildings, "Состав этапа не может быть null: этап без построек задают пустой картой"));
            for (Map.Entry<BoardCase, Integer> entry : buildings.entrySet()) {
                if (entry.getValue() < 1) {
                    throw new IllegalArgumentException("Этап " + stage + ": уровень " + entry.getKey() + " = "
                            + entry.getValue() + ", а журнал доски отмечает уровни с первого");
                }
            }
        }
    }

    public StageRules {
        stages = List.copyOf(Objects.requireNonNull(stages, "Этапы не могут быть null"));
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

    public int stageOf(BoardLedger ledger) {
        Objects.requireNonNull(ledger, "Журнал доски не может быть null: этап читается по построенному");
        for (Stage stage : stages) {
            if (!missing(stage, ledger).isEmpty()) {
                return stage.stage();
            }
        }
        return stages.size() + 1;
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
                        buildings(node.get("buildings"), origin)));
            }
            return new StageRules(stages);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    public static StageRules load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    private static Map<BoardCase, Integer> buildings(JsonNode node, String origin) {
        if (node == null || !node.isObject()) {
            throw new IllegalStateException("Файл '" + origin + "': поле 'buildings' — не объект «кейс → уровень»");
        }
        Map<BoardCase, Integer> buildings = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            BoardCase boardCase;
            try {
                boardCase = BoardCase.valueOf(field.getKey());
            } catch (IllegalArgumentException unknown) {
                throw new IllegalStateException(
                        "Файл '" + origin + "': неизвестный кейс постройки '" + field.getKey() + "'", unknown);
            }
            buildings.put(boardCase, JsonResource.integer(field.getValue(), origin, field.getKey()));
        }
        return buildings;
    }
}
