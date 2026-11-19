package com.theminespirit.core.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.engine.TaskProgress.ConditionAt;
import com.theminespirit.core.engine.TaskProgress.ConditionBranch;
import com.theminespirit.core.engine.TaskProgress.FallbackAt;
import com.theminespirit.core.engine.TaskProgress.ParallelAt;
import com.theminespirit.core.engine.TaskProgress.ParallelChild;
import com.theminespirit.core.engine.TaskProgress.RepeatAt;
import com.theminespirit.core.engine.TaskProgress.ScriptAt;
import com.theminespirit.core.engine.TaskProgress.SequenceAt;
import com.theminespirit.core.plan.PlanParser;
import com.theminespirit.core.plan.PlanValidationException;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

final class TaskSnapshotDecoder {
    private TaskSnapshotDecoder() {}

    static TaskSnapshot decode(String json) {
        Objects.requireNonNull(json, "json не может быть null: снимок приходит текстом из ключа SpiritTask");
        JsonNode root = readTree(json);
        if (!root.isObject()) {
            throw new SnapshotFormatException("Снимок задачи должен быть JSON-объектом, а получено " + root
                    + ": проверьте ключ SpiritTask, файл не правьте вручную");
        }
        int version = requireVersion(root);
        RunStatus status = parseStatus(root);
        String reason = root.hasNonNull(TaskSnapshotCodec.REASON)
                ? root.get(TaskSnapshotCodec.REASON).asText()
                : null;
        if (status != RunStatus.RUNNING) {
            return new TaskSnapshot(version, status, reason, null, null);
        }
        TaskNode plan = parsePlan(root.get("plan"));
        TaskProgress progress = parseProgress(root.get("progress"));
        TaskSnapshotValidator.checkAgainstPlan(plan, progress);
        return new TaskSnapshot(version, status, reason, plan, progress);
    }

    private static JsonNode readTree(String json) {
        try {
            return TaskSnapshotCodec.MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new SnapshotFormatException(
                    "Снимок задачи не разобран как JSON: проверьте синтаксис значения "
                            + "ключа SpiritTask, файл не правьте вручную",
                    e);
        }
    }

    private static int requireVersion(JsonNode root) {
        JsonNode versionNode = root.get("version");
        if (versionNode == null || !versionNode.isIntegralNumber() || !versionNode.canConvertToInt()) {
            throw new SnapshotFormatException(
                    "У снимка задачи нет целого поля version: без версии читатель " + "не выберет разбор формата");
        }
        int version = versionNode.intValue();
        if (version > TaskSnapshotCodec.MAX_VERSION) {
            throw new SnapshotFormatException(
                    "Версия снимка " + version + " новее поддерживаемой " + TaskSnapshotCodec.MAX_VERSION
                            + ": обнови мод или добавь читателя старой версии, файл не правьте вручную");
        }
        if (version < 1) {
            throw new SnapshotFormatException(
                    "Версия снимка " + version + " меньше 1: допустимая версия начинается с 1");
        }
        return version;
    }

    private static RunStatus parseStatus(JsonNode root) {
        JsonNode statusNode = root.get(TaskSnapshotCodec.STATUS);
        if (statusNode == null || !statusNode.isTextual()) {
            throw new SnapshotFormatException("У снимка задачи нет строкового поля status: без него неизвестно, "
                    + "продолжать задачу или показывать итог");
        }
        try {
            return RunStatus.valueOf(statusNode.asText());
        } catch (IllegalArgumentException e) {
            throw new SnapshotFormatException(
                    "Неизвестный статус задачи '" + statusNode.asText()
                            + "': допустимы RUNNING, SUCCEEDED, FAILED, CANCELLED",
                    e);
        }
    }

    private static TaskNode parsePlan(JsonNode node) {
        if (node == null || node.isNull()) {
            throw new SnapshotFormatException("RUNNING-снимок без плана plan: движку нечего продолжать");
        }
        try {
            return PlanParser.parseSnapshotStructure(node.toString());
        } catch (PlanValidationException e) {
            throw new SnapshotFormatException("План снимка не разобран: " + e.getMessage(), e);
        }
    }

    private static TaskProgress parseProgress(JsonNode node) {
        if (node == null || node.isNull()) {
            throw new SnapshotFormatException(
                    "RUNNING-снимок без прогресса progress: движок не знает, " + "с какого шага продолжать");
        }
        if (!node.isObject()) {
            throw new SnapshotFormatException("Прогресс снимка должен быть JSON-объектом, а получено " + node);
        }
        String type = requireText(node, TaskSnapshotCodec.TYPE, "прогресса");
        return switch (type) {
            case TaskSnapshotCodec.SEQUENCE ->
                new SequenceAt(requireIndex(node, TaskSnapshotCodec.SEQUENCE), optionalChild(node));
            case TaskSnapshotCodec.REPEAT ->
                new RepeatAt(requireIndex(node, TaskSnapshotCodec.REPEAT), optionalChild(node));
            case TaskSnapshotCodec.FALLBACK ->
                new FallbackAt(
                        requireIndex(node, TaskSnapshotCodec.FALLBACK),
                        optionalText(node, "lastReason"),
                        optionalChild(node));
            case "parallel" -> new ParallelAt(parseParallelChildren(node.get("children")));
            case "condition" -> new ConditionAt(parseBranch(node), optionalChild(node));
            case "script" -> new ScriptAt(parseState(node.get("state")));
            default ->
                throw new SnapshotFormatException("Неизвестный тип прогресса '" + type
                        + "': допустимы sequence, repeat, fallback, parallel, condition, script");
        };
    }

    private static List<ParallelChild> parseParallelChildren(JsonNode node) {
        if (node == null || !node.isArray()) {
            throw new SnapshotFormatException(
                    "У прогресса parallel поле 'children' обязательно и должно быть массивом");
        }
        List<ParallelChild> children = new ArrayList<>(node.size());
        for (JsonNode child : node) {
            if (!child.isObject()) {
                throw new SnapshotFormatException(
                        "Элемент children прогресса parallel должен быть объектом, " + "а получено " + child);
            }
            ScriptStatus status = parseChildStatus(child);
            String reason = optionalText(child, TaskSnapshotCodec.REASON);
            JsonNode grandchild = child.get(TaskSnapshotCodec.CHILD);
            TaskProgress parsed = grandchild == null || grandchild.isNull() ? null : parseProgress(grandchild);
            children.add(new ParallelChild(status, reason, parsed));
        }
        return children;
    }

    private static ScriptStatus parseChildStatus(JsonNode child) {
        String statusText = requireText(child, TaskSnapshotCodec.STATUS, "элемента children прогресса parallel");
        try {
            return ScriptStatus.valueOf(statusText);
        } catch (IllegalArgumentException e) {
            throw new SnapshotFormatException(
                    "Неизвестный статус потомка parallel '" + statusText + "': допустимы RUNNING, SUCCEEDED, FAILED",
                    e);
        }
    }

    private static ConditionBranch parseBranch(JsonNode node) {
        String branch = requireText(node, "branch", "прогресса condition");
        return switch (branch) {
            case "unresolved" -> ConditionBranch.UNRESOLVED;
            case "then" -> ConditionBranch.THEN;
            case "else" -> ConditionBranch.ELSE;
            default ->
                throw new SnapshotFormatException(
                        "Неизвестная ветка прогресса condition '" + branch + "': допустимы unresolved, then, else");
        };
    }

    private static @Nullable TaskProgress optionalChild(JsonNode node) {
        JsonNode child = node.get(TaskSnapshotCodec.CHILD);
        return child == null || child.isNull() ? null : parseProgress(child);
    }

    private static @Nullable String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static int requireIndex(JsonNode node, String owner) {
        JsonNode indexNode = node.get("index");
        if (indexNode == null || !indexNode.isIntegralNumber() || !indexNode.canConvertToInt()) {
            throw new SnapshotFormatException(
                    "У прогресса '" + owner + "' поле 'index' обязательно и должно быть целым числом в диапазоне int");
        }
        int index = indexNode.intValue();
        if (index < 0) {
            throw new SnapshotFormatException(
                    "У прогресса '" + owner + "' отрицательный index=" + index + ": позиция шага нумеруется с нуля");
        }
        return index;
    }

    private static String requireText(JsonNode node, String field, String owner) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new SnapshotFormatException("У " + owner + " поле '" + field + "' обязательно и должно быть строкой");
        }
        return value.textValue();
    }

    private static Map<String, Object> parseState(@Nullable JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw new SnapshotFormatException("Состояние скрипта должно быть JSON-объектом, а получено " + node);
        }
        Map<String, Object> state = new LinkedHashMap<>();
        node.fields()
                .forEachRemaining(entry -> state.put(entry.getKey(), parseValue(entry.getValue(), entry.getKey())));
        return state;
    }

    private static Object parseValue(JsonNode node, String path) {
        if (node == null || node.isNull()) {
            throw new SnapshotFormatException("Состояние скрипта содержит null в '" + path
                    + "': состояние хранит только JSON-совместимые значения");
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
            List<Object> values = new ArrayList<>(node.size());
            for (int i = 0; i < node.size(); i++) {
                values.add(parseValue(node.get(i), path + "[" + i + "]"));
            }
            return List.copyOf(values);
        }
        if (node.isObject()) {
            Map<String, Object> values = new LinkedHashMap<>();
            node.fields()
                    .forEachRemaining(entry ->
                            values.put(entry.getKey(), parseValue(entry.getValue(), path + "." + entry.getKey())));
            return values;
        }
        throw new SnapshotFormatException("Состояние скрипта содержит недопустимое значение в '" + path + "' ("
                + node.getNodeType() + "): состояние хранит только JSON-совместимые значения");
    }
}
