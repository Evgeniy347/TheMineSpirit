package com.theminespirit.core.plan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class PlanJson {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PlanJson() {}

    public static String toJson(TaskNode node) {
        Objects.requireNonNull(node, "node не может быть null: сериализовать нечего");
        try {
            return MAPPER.writeValueAsString(toJsonNode(node));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "План не сериализован в JSON: проверьте значения параметров на JSON-совместимость", e);
        }
    }

    static JsonNode toJsonNode(TaskNode node) {
        return switch (node) {
            case ScriptCall call -> scriptToJson(call);
            case SequenceNode sequence -> stepsToJson("sequence", sequence.steps());
            case FallbackNode fallback -> stepsToJson("fallback", fallback.steps());
            case ParallelNode parallel -> parallelToJson(parallel);
            case RepeatNode repeat -> repeatToJson(repeat);
            case ConditionNode condition -> conditionToJson(condition);
        };
    }

    private static ObjectNode scriptToJson(ScriptCall call) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "script");
        node.put("script", call.script());
        node.set("params", paramsToJson(call.params()));
        return node;
    }

    private static ObjectNode stepsToJson(String type, List<TaskNode> steps) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", type);
        ArrayNode array = node.putArray("steps");
        for (TaskNode step : steps) {
            array.add(toJsonNode(step));
        }
        return node;
    }

    private static ObjectNode parallelToJson(ParallelNode parallel) {
        ObjectNode node = stepsToJson("parallel", parallel.steps());
        node.put("mode", parallel.mode() == ParallelMode.ALL ? "all" : "any");
        return node;
    }

    private static ObjectNode repeatToJson(RepeatNode repeat) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "repeat");
        node.put("times", repeat.times());
        node.set("step", toJsonNode(repeat.step()));
        return node;
    }

    private static ObjectNode conditionToJson(ConditionNode condition) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "condition");
        node.put("check", condition.check());
        node.set("params", paramsToJson(condition.params()));
        node.set("then", toJsonNode(condition.thenStep()));
        if (condition.elseStep() != null) {
            node.set("else", toJsonNode(condition.elseStep()));
        }
        return node;
    }

    private static ObjectNode paramsToJson(Map<String, Object> params) {
        ObjectNode node = MAPPER.createObjectNode();
        for (Map.Entry<String, Object> entry : new TreeMap<>(params).entrySet()) {
            node.set(entry.getKey(), valueToJson(entry.getKey(), entry.getValue()));
        }
        return node;
    }

    private static JsonNode valueToJson(String name, Object value) {
        Objects.requireNonNull(value, "Параметр '" + name + "' равен null: null-значение недопустимо в плане");
        if (value instanceof String text) {
            return MAPPER.getNodeFactory().textNode(text);
        }
        if (value instanceof Boolean flag) {
            return MAPPER.getNodeFactory().booleanNode(flag);
        }
        if (value instanceof Integer number) {
            return MAPPER.getNodeFactory().numberNode(number);
        }
        if (value instanceof Long number) {
            return MAPPER.getNodeFactory().numberNode(number);
        }
        if (value instanceof Double number) {
            return MAPPER.getNodeFactory().numberNode(number);
        }
        if (value instanceof List<?> list) {
            ArrayNode array = MAPPER.createArrayNode();
            for (Object element : list) {
                array.add(valueToJson(name, element));
            }
            return array;
        }
        if (value instanceof Map<?, ?> map) {
            ObjectNode object = MAPPER.createObjectNode();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                object.set(key, valueToJson(key, entry.getValue()));
            }
            return object;
        }
        throw new IllegalArgumentException("Параметр '" + name + "' типа "
                + value.getClass().getSimpleName() + " не является JSON-совместимым: допустимы строки, числа, "
                + "булевы значения, списки и карты из них");
    }
}
