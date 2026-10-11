package com.theminespirit.core.plan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.script.ParamSpec;
import com.theminespirit.core.script.ParamType;
import com.theminespirit.core.script.ScriptCatalog;
import com.theminespirit.core.script.ScriptSpec;
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
        Objects.requireNonNull(catalog, "catalog не может быть null: план валидируется по каталогу скриптов");
        JsonNode root = readTree(json);
        return parseNode(root, catalog, 1, new Counter());
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

    private static TaskNode parseNode(JsonNode node, ScriptCatalog catalog, int depth, Counter counter) {
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
            case "script" -> parseScript(node, catalog, depth, counter);
            case "sequence" -> parseSequence(node, catalog, depth, counter);
            case "repeat" -> parseRepeat(node, catalog, depth, counter);
            case "fallback" -> parseFallback(node, catalog, depth, counter);
            case "parallel" -> parseParallel(node, catalog, depth, counter);
            case "condition" -> parseCondition(node, catalog, depth, counter);
            default -> throw new PlanValidationException("Неизвестный тип узла плана '" + type
                    + "': допустимы script, sequence, repeat, fallback, parallel, condition");
        };
    }

    private static TaskNode parseScript(JsonNode node, ScriptCatalog catalog, int depth, Counter counter) {
        String scriptId = requiredText(node, "script", "узел script");
        ScriptSpec spec = catalog.find(scriptId).orElseThrow(() -> new PlanValidationException(
                "Неизвестный скрипт '" + scriptId + "': выберите скрипт из каталога /v1/capabilities"));
        Map<String, Object> params = validateParams(node.get("params"), spec);
        return new ScriptCall(scriptId, params);
    }

    private static TaskNode parseSequence(JsonNode node, ScriptCatalog catalog, int depth, Counter counter) {
        return new SequenceNode(parseSteps(node, catalog, depth, counter, "sequence"));
    }

    private static TaskNode parseFallback(JsonNode node, ScriptCatalog catalog, int depth, Counter counter) {
        return new FallbackNode(parseSteps(node, catalog, depth, counter, "fallback"));
    }

    private static TaskNode parseRepeat(JsonNode node, ScriptCatalog catalog, int depth, Counter counter) {
        JsonNode times = node.get("times");
        if (times == null || !times.isIntegralNumber() || !times.canConvertToInt()) {
            throw new PlanValidationException(
                    "У узла repeat поле 'times' обязательно и должно быть целым числом в диапазоне int");
        }
        JsonNode step = node.get("step");
        if (step == null) {
            throw new PlanValidationException("У узла repeat поле 'step' обязательно: нечего повторять");
        }
        return new RepeatNode(times.intValue(), parseNode(step, catalog, depth + 1, counter));
    }

    private static TaskNode parseParallel(JsonNode node, ScriptCatalog catalog, int depth, Counter counter) {
        String modeText = requiredText(node, "mode", "узел parallel");
        ParallelMode mode = switch (modeText) {
            case "all" -> ParallelMode.ALL;
            case "any" -> ParallelMode.ANY;
            default -> throw new PlanValidationException(
                    "Неизвестный режим parallel '" + modeText + "': допустимы 'all' и 'any'");
        };
        return new ParallelNode(mode, parseSteps(node, catalog, depth, counter, "parallel"));
    }

    private static TaskNode parseCondition(JsonNode node, ScriptCatalog catalog, int depth, Counter counter) {
        String check = requiredText(node, "check", "узел condition");
        Map<String, Object> params = toPlainObject(node.get("params"), "узел condition");
        JsonNode thenNode = node.get("then");
        if (thenNode == null) {
            throw new PlanValidationException("У узла condition поле 'then' обязательно: у условия нужна ветка then");
        }
        TaskNode thenStep = parseNode(thenNode, catalog, depth + 1, counter);
        JsonNode elseNode = node.get("else");
        TaskNode elseStep = elseNode == null || elseNode.isNull()
                ? null
                : parseNode(elseNode, catalog, depth + 1, counter);
        return new ConditionNode(check, params, thenStep, elseStep);
    }

    private static List<TaskNode> parseSteps(JsonNode node, ScriptCatalog catalog, int depth, Counter counter,
            String owner) {
        JsonNode steps = node.get("steps");
        if (steps == null || !steps.isArray() || steps.isEmpty()) {
            throw new PlanValidationException(
                    "У узла " + owner + " поле 'steps' обязательно и должно быть непустым массивом");
        }
        List<TaskNode> parsed = new ArrayList<>();
        for (JsonNode step : steps) {
            parsed.add(parseNode(step, catalog, depth + 1, counter));
        }
        return parsed;
    }

    private static Map<String, Object> validateParams(JsonNode paramsNode, ScriptSpec spec) {
        Map<String, ParamSpec> schema = new LinkedHashMap<>();
        for (ParamSpec param : spec.params()) {
            schema.put(param.name(), param);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        if (paramsNode != null && !paramsNode.isNull() && !paramsNode.isObject()) {
            throw new PlanValidationException(
                    "Параметры скрипта '" + spec.id() + "' должны быть JSON-объектом");
        }
        if (paramsNode != null && paramsNode.isObject()) {
            Iterator<String> names = paramsNode.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                ParamSpec param = schema.get(name);
                if (param == null) {
                    throw new PlanValidationException("Неизвестный параметр '" + name + "' у скрипта '" + spec.id()
                            + "': сверьте имя с каталогом /v1/capabilities");
                }
                result.put(name, convertParam(paramsNode.get(name), param, spec.id()));
            }
        }
        for (ParamSpec param : spec.params()) {
            if (param.required() && !result.containsKey(param.name())) {
                throw new PlanValidationException("У скрипта '" + spec.id() + "' не задан обязательный параметр '"
                        + param.name() + "' (" + param.type() + ")");
            }
        }
        return result;
    }

    private static Object convertParam(JsonNode node, ParamSpec param, String scriptId) {
        return switch (param.type()) {
            case INT -> {
                if (!node.isIntegralNumber() || !node.canConvertToInt()) {
                    throw typeError(param, scriptId, "целое число в диапазоне int");
                }
                yield node.intValue();
            }
            case DOUBLE -> {
                if (!node.isNumber()) {
                    throw typeError(param, scriptId, "число");
                }
                yield node.doubleValue();
            }
            case STRING -> {
                if (!node.isTextual()) {
                    throw typeError(param, scriptId, "строку");
                }
                yield node.textValue();
            }
            case STRING_LIST -> convertStringList(node, param, scriptId);
            case BOOL -> {
                if (!node.isBoolean()) {
                    throw typeError(param, scriptId, "true или false");
                }
                yield node.booleanValue();
            }
            case ENUM -> {
                if (!node.isTextual()) {
                    throw typeError(param, scriptId, "строку с допустимым значением");
                }
                yield node.textValue();
            }
        };
    }

    private static List<String> convertStringList(JsonNode node, ParamSpec param, String scriptId) {
        if (!node.isArray()) {
            throw typeError(param, scriptId, "массив строк");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode element : node) {
            if (!element.isTextual()) {
                throw typeError(param, scriptId, "массив строк");
            }
            values.add(element.textValue());
        }
        return List.copyOf(values);
    }

    private static PlanValidationException typeError(ParamSpec param, String scriptId, String expected) {
        return new PlanValidationException("Параметр '" + param.name() + "' скрипта '" + scriptId + "' ожидает " + expected
                + ": сверьте тип со схемой параметров в /v1/capabilities");
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
            result.put(name, toPlainValue(node.get(name)));
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
            return node.isInt() ? (Object) node.intValue() : (Object) node.longValue();
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
