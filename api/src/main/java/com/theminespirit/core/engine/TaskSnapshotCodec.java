package com.theminespirit.core.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.theminespirit.core.engine.TaskProgress.ConditionAt;
import com.theminespirit.core.engine.TaskProgress.FallbackAt;
import com.theminespirit.core.engine.TaskProgress.ParallelAt;
import com.theminespirit.core.engine.TaskProgress.ParallelChild;
import com.theminespirit.core.engine.TaskProgress.RepeatAt;
import com.theminespirit.core.engine.TaskProgress.ScriptAt;
import com.theminespirit.core.engine.TaskProgress.SequenceAt;
import com.theminespirit.core.plan.PlanJson;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class TaskSnapshotCodec {
    static final String STATUS = "status";
    static final String REASON = "reason";
    static final String TYPE = "type";
    static final String SEQUENCE = "sequence";
    static final String REPEAT = "repeat";
    static final String FALLBACK = "fallback";
    static final String CHILD = "child";
    static final ObjectMapper MAPPER = new ObjectMapper();
    static final int MAX_VERSION = 1;

    private TaskSnapshotCodec() {}

    public static String encode(TaskSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot не может быть null: сохранять нечего");
        ObjectNode root = MAPPER.createObjectNode();
        root.put("version", snapshot.version());
        root.put(STATUS, snapshot.status().name());
        if (snapshot.reason() != null) {
            root.put(REASON, snapshot.reason());
        }
        if (snapshot.running()) {
            root.set("plan", planToJson(snapshot.runningPlan()));
            root.set("progress", progressToJson(snapshot.runningProgress()));
        }
        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Снимок задачи не сериализован в JSON: проверьте значения состояния скрипта", e);
        }
    }

    public static TaskSnapshot decode(String json) {
        return TaskSnapshotDecoder.decode(json);
    }

    private static JsonNode planToJson(TaskNode plan) {
        try {
            return MAPPER.readTree(PlanJson.toJson(plan));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("План задачи не превращён в дерево JSON: проверьте значения параметров", e);
        }
    }

    private static ObjectNode progressToJson(TaskProgress progress) {
        return switch (progress) {
            case SequenceAt at -> indexedToJson(SEQUENCE, at.index(), at.child());
            case RepeatAt at -> indexedToJson(REPEAT, at.index(), at.child());
            case FallbackAt at -> fallbackToJson(at);
            case ParallelAt at -> parallelToJson(at);
            case ConditionAt at -> conditionToJson(at);
            case ScriptAt at -> scriptToJson(at);
        };
    }

    private static ObjectNode indexedToJson(String type, int index, @Nullable TaskProgress child) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put(TYPE, type);
        node.put("index", index);
        if (child != null) {
            node.set(CHILD, progressToJson(child));
        }
        return node;
    }

    private static ObjectNode fallbackToJson(FallbackAt at) {
        ObjectNode node = indexedToJson(FALLBACK, at.index(), at.child());
        if (at.lastReason() != null) {
            node.put("lastReason", at.lastReason());
        }
        return node;
    }

    private static ObjectNode parallelToJson(ParallelAt at) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put(TYPE, "parallel");
        ArrayNode children = node.putArray("children");
        for (ParallelChild child : at.children()) {
            ObjectNode entry = children.addObject();
            entry.put(STATUS, child.status().name());
            if (child.reason() != null) {
                entry.put(REASON, child.reason());
            }
            if (child.child() != null) {
                entry.set(CHILD, progressToJson(child.child()));
            }
        }
        return node;
    }

    private static ObjectNode conditionToJson(ConditionAt at) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put(TYPE, "condition");
        node.put("branch", at.branch().name().toLowerCase(Locale.ROOT));
        if (at.child() != null) {
            node.set(CHILD, progressToJson(at.child()));
        }
        return node;
    }

    private static ObjectNode scriptToJson(ScriptAt at) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put(TYPE, "script");
        node.set("state", stateToJson(at.state()));
        return node;
    }

    private static ObjectNode stateToJson(Map<String, Object> state) {
        ObjectNode node = MAPPER.createObjectNode();
        for (Map.Entry<String, Object> entry : state.entrySet()) {
            node.set(entry.getKey(), valueToJson(entry.getKey(), entry.getValue()));
        }
        return node;
    }

    private static JsonNode valueToJson(String path, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Состояние скрипта в '" + path + "' равно null: состояние хранит только "
                    + "JSON-совместимые значения");
        }
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
            for (int i = 0; i < list.size(); i++) {
                array.add(valueToJson(path + "[" + i + "]", list.get(i)));
            }
            return array;
        }
        if (value instanceof Map<?, ?> map) {
            ObjectNode object = MAPPER.createObjectNode();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("Ключ состояния '" + entry.getKey() + "' в '" + path
                            + "' не строка: JSON-объект хранит только строковые ключи");
                }
                object.set(key, valueToJson(path + "." + key, entry.getValue()));
            }
            return object;
        }
        throw new IllegalArgumentException("Состояние скрипта в '" + path + "' имеет тип "
                + value.getClass().getSimpleName()
                + ": допустимы строки, числа, булевы значения, списки и карты из них");
    }

    public static SaveEncoded encodeForSave(TaskSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot не может быть null: сохранять нечего");
        try {
            return new SaveEncoded(encode(snapshot), null);
        } catch (RuntimeException e) {
            String reason = "не удалось сохранить задачу: " + e.getMessage();
            return new SaveEncoded(
                    encode(new TaskSnapshot(TaskSnapshot.VERSION, RunStatus.FAILED, reason, null, null)), reason);
        }
    }

    public record SaveEncoded(String json, @Nullable String failureReason) {}
}
