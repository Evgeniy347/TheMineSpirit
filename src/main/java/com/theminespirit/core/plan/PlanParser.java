package com.theminespirit.core.plan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.ScriptCatalog;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PlanParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_DEPTH = 16;
    private static final int MAX_NODES = 256;

    private PlanParser() {
    }

    public static TaskNode parse(String json, ScriptCatalog catalog) {
        return parse(json, catalog, ConditionCatalog.empty());
    }

    public static TaskNode parse(String json, ScriptCatalog catalog, ConditionCatalog conditions) {
        Objects.requireNonNull(catalog, "catalog не может быть null: план валидируется по каталогу скриптов");
        Objects.requireNonNull(conditions, "conditions не может быть null: каталог проверок обязателен");
        JsonNode root = readTree(json);
        TaskNode node = parseNode(root, 1, new Counter());
        return PlanValidator.validate(node, catalog, conditions);
    }

    private static JsonNode readTree(String json) {
        Objects.requireNonNull(json, "json не может быть null: план приходит текстом");
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new PlanValidationException(
                    "План не разобран как JSON: проверьте синтаксис, кавычки и кодировку UTF-8", e);
        }
    }

    private static TaskNode parseNode(JsonNode node, int depth, Counter counter) {
        if (depth > MAX_DEPTH) {
            throw new PlanValidationException("Глубина плана больше " + MAX_DEPTH
                    + ": проверьте вложенность sequence/repeat/fallback/parallel/condition, планировщик зациклился");
        }
        counter.increment();
        if (counter.value() > MAX_NODES) {
            throw new PlanValidationException("В плане больше " + MAX_NODES
                    + " узлов: проверьте план, ИИ-планировщик вернул неограниченно большое дерево");
        }
        if (node == null || !node.isObject()) {
            throw new PlanValidationException("Узел плана должен быть JSON-объектом с полем 'type', а получено " + node);
        }
        String type = requiredText(node, "type", "узел плана");
        return switch (type) {
            case "script" -> parseScript(node);
            case "sequence" -> parseSequence(node, depth, counter);
            case "repeat" -> parseRepeat(node, depth, counter);
            case "fallback" -> parseFallback(node, depth, counter);
            case "parallel" -> parseParallel(node, depth, counter);
            case "condition" -> parseCondition(node, depth, counter);
            default -> throw new PlanValidationException("Неизвестный тип узла плана '" + type
                    + "': допустимы script, sequence, repeat, fallback, parallel, condition");
        };
    }

    private static TaskNode parseScript(JsonNode node) {
        String scriptId = requiredText(node, "script", "узел script");
        Map<String, Object> params = toPlainObject(node.get("params"), "скрипта '" + scriptId + "'");
        return new ScriptCall(scriptId, params);
    }

    private static TaskNode parseSequence(JsonNode node, int depth, Counter counter) {
        return new SequenceNode(parseSteps(node, depth, counter, "sequence"));
    }

    private static TaskNode parseFallback(JsonNode node, int depth, Counter counter) {
        return new FallbackNode(parseSteps(node, depth, counter, "fallback"));
    }

    private static TaskNode parseRepeat(JsonNode node, int depth, Counter counter) {
        JsonNode times = node.get("times");
        if (times == null || !times.isIntegralNumber() || !times.canConvertToInt()) {
            throw new PlanValidationException(
                    "У узла repeat поле 'times' обязательно и должно быть целым числом в диапазоне int");
        }
        JsonNode step = node.get("step");
        if (step == null) {
            throw new PlanValidationException("У узла repeat поле 'step' обязательно: нечего повторять");
        }
        return new RepeatNode(times.intValue(), parseNode(step, depth + 1, counter));
    }

    private static TaskNode parseParallel(JsonNode node, int depth, Counter counter) {
        String modeText = requiredText(node, "mode", "узел parallel");
        ParallelMode mode = switch (modeText) {
            case "all" -> ParallelMode.ALL;
            case "any" -> ParallelMode.ANY;
            default -> throw new PlanValidationException(
                    "Неизвестный режим parallel '" + modeText + "': допустимы 'all' и 'any'");
        };
        return new ParallelNode(mode, parseSteps(node, depth, counter, "parallel"));
    }

    private static TaskNode parseCondition(JsonNode node, int depth, Counter counter) {
        String check = requiredText(node, "check", "узел condition");
        Map<String, Object> params = toPlainObject(node.get("params"), "проверки '" + check + "'");
        JsonNode thenNode = node.get("then");
        if (thenNode == null) {
            throw new PlanValidationException("У узла condition поле 'then' обязательно: у условия нужна ветка then");
        }
        TaskNode thenStep = parseNode(thenNode, depth + 1, counter);
        JsonNode elseNode = node.get("else");
        TaskNode elseStep = elseNode == null || elseNode.isNull()
                ? null
                : parseNode(elseNode, depth + 1, counter);
        return new ConditionNode(check, params, thenStep, elseStep);
    }

    private static List<TaskNode> parseSteps(JsonNode node, int depth, Counter counter, String owner) {
        JsonNode steps = node.get("steps");
        if (steps == null || !steps.isArray() || steps.isEmpty()) {
            throw new PlanValidationException(
                    "У узла " + owner + " поле 'steps' обязательно и должно быть непустым массивом");
        }
        List<TaskNode> parsed = new ArrayList<>();
        for (JsonNode step : steps) {
            parsed.add(parseNode(step, depth + 1, counter));
        }
        return parsed;
    }

    private static Map<String, Object> toPlainObject(JsonNode node, String owner) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (node == null || node.isNull()) {
            return result;
        }
        if (!node.isObject()) {
            throw new PlanValidationException("Параметры " + owner + " должны быть JSON-объектом");
        }
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            Object value = toPlainValue(node.get(name));
            if (value != null) {
                result.put(name, value);
            }
        }
        return result;
    }

    private static Object toPlainValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isIntegralNumber()) {
            if (node.canConvertToInt()) {
                return node.intValue();
            }
            return node.longValue();
        }
        if (node.isNumber()) {
            return node.doubleValue();
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            for (JsonNode element : node) {
                values.add(toPlainValue(element));
            }
            return List.copyOf(values);
        }
        return toPlainObject(node, "узла");
    }

    private static String requiredText(JsonNode node, String field, String owner) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new PlanValidationException("У " + owner + " поле '" + field + "' обязательно и должно быть строкой");
        }
        return value.textValue();
    }

    private static final class Counter {
        private int nodes;

        void increment() {
            nodes++;
        }

        int value() {
            return nodes;
        }
    }
}
