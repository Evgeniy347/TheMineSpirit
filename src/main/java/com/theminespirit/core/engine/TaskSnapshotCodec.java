package com.theminespirit.core.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.theminespirit.core.engine.TaskProgress.ConditionAt;
import com.theminespirit.core.engine.TaskProgress.ConditionBranch;
import com.theminespirit.core.engine.TaskProgress.FallbackAt;
import com.theminespirit.core.engine.TaskProgress.ParallelAt;
import com.theminespirit.core.engine.TaskProgress.ParallelChild;
import com.theminespirit.core.engine.TaskProgress.RepeatAt;
import com.theminespirit.core.engine.TaskProgress.ScriptAt;
import com.theminespirit.core.engine.TaskProgress.SequenceAt;
import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.PlanJson;
import com.theminespirit.core.plan.PlanParser;
import com.theminespirit.core.plan.PlanValidationException;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class TaskSnapshotCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_VERSION = 1;

    private TaskSnapshotCodec() {
    }

    public static String encode(TaskSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot не может быть null: сохранять нечего");
        ObjectNode root = MAPPER.createObjectNode();
        root.put("version", snapshot.version());
        root.put("status", snapshot.status().name());
        if (snapshot.reason() != null) {
            root.put("reason", snapshot.reason());
        }
        if (snapshot.running()) {
            root.set("plan", planToJson(snapshot.plan()));
            root.set("progress", progressToJson(snapshot.progress()));
        }
        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Снимок задачи не сериализован в JSON: проверьте значения состояния скрипта", e);
        }
    }

    public static TaskSnapshot decode(String json) {
        Objects.requireNonNull(json, "json не может быть null: снимок приходит текстом из ключа SpiritTask");
        JsonNode root = readTree(json);
        if (!root.isObject()) {
            throw new SnapshotFormatException("Снимок задачи должен быть JSON-объектом, а получено " + root
                    + ": проверьте ключ SpiritTask, файл не правьте вручную");
        }
        int version = requireVersion(root);
        RunStatus status = parseStatus(root);
        String reason = root.hasNonNull("reason") ? root.get("reason").asText() : null;
        if (status != RunStatus.RUNNING) {
            return new TaskSnapshot(version, status, reason, null, null);
        }
        TaskNode plan = parsePlan(root.get("plan"));
        TaskProgress progress = parseProgress(root.get("progress"));
        checkAgainstPlan(plan, progress);
        return new TaskSnapshot(version, status, reason, plan, progress);
    }

    private static JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new SnapshotFormatException("Снимок задачи не разобран как JSON: проверьте синтаксис значения "
                    + "ключа SpiritTask, файл не правьте вручную", e);
        }
    }

    private static int requireVersion(JsonNode root) {
        JsonNode versionNode = root.get("version");
        if (versionNode == null || !versionNode.isIntegralNumber() || !versionNode.canConvertToInt()) {
            throw new SnapshotFormatException("У снимка задачи нет целого поля version: без версии читатель "
                    + "не выберет разбор формата");
        }
        int version = versionNode.intValue();
        if (version > MAX_VERSION) {
            throw new SnapshotFormatException("Версия снимка " + version + " новее поддерживаемой " + MAX_VERSION
                    + ": обнови мод или добавь читателя старой версии, файл не правьте вручную");
        }
        if (version < 1) {
            throw new SnapshotFormatException("Версия снимка " + version + " меньше 1: допустимая версия начинается с 1");
        }
        return version;
    }

    private static RunStatus parseStatus(JsonNode root) {
        JsonNode statusNode = root.get("status");
        if (statusNode == null || !statusNode.isTextual()) {
            throw new SnapshotFormatException("У снимка задачи нет строкового поля status: без него неизвестно, "
                    + "продолжать задачу или показывать итог");
        }
        try {
            return RunStatus.valueOf(statusNode.asText());
        } catch (IllegalArgumentException e) {
            throw new SnapshotFormatException("Неизвестный статус задачи '" + statusNode.asText()
                    + "': допустимы RUNNING, SUCCEEDED, FAILED, CANCELLED", e);
        }
    }

    private static TaskNode parsePlan(JsonNode node) {
        if (node == null || node.isNull()) {
            throw new SnapshotFormatException("RUNNING-снимок без плана plan: движку нечего продолжать");
        }
        try {
            return PlanParser.parseStructure(node.toString());
        } catch (PlanValidationException e) {
            throw new SnapshotFormatException("План снимка не разобран: " + e.getMessage(), e);
        }
    }

    private static TaskProgress parseProgress(JsonNode node) {
        if (node == null || node.isNull()) {
            throw new SnapshotFormatException("RUNNING-снимок без прогресса progress: движок не знает, "
                    + "с какого шага продолжать");
        }
        if (!node.isObject()) {
            throw new SnapshotFormatException("Прогресс снимка должен быть JSON-объектом, а получено " + node);
        }
        String type = requireText(node, "type", "прогресса");
        return switch (type) {
            case "sequence" -> new SequenceAt(requireIndex(node, "sequence"), optionalChild(node, "sequence"));
            case "repeat" -> new RepeatAt(requireIndex(node, "repeat"), optionalChild(node, "repeat"));
            case "fallback" -> new FallbackAt(requireIndex(node, "fallback"),
                    optionalText(node, "lastReason"), optionalChild(node, "fallback"));
            case "parallel" -> new ParallelAt(parseParallelChildren(node.get("children")));
            case "condition" -> new ConditionAt(parseBranch(node), optionalChild(node, "condition"));
            case "script" -> new ScriptAt(parseState(node.get("state")));
            default -> throw new SnapshotFormatException("Неизвестный тип прогресса '" + type
                    + "': допустимы sequence, repeat, fallback, parallel, condition, script");
        };
    }

    private static List<ParallelChild> parseParallelChildren(JsonNode node) {
        if (node == null || !node.isArray()) {
            throw new SnapshotFormatException("У прогресса parallel поле 'children' обязательно и должно быть массивом");
        }
        List<ParallelChild> children = new ArrayList<>(node.size());
        for (JsonNode child : node) {
            if (!child.isObject()) {
                throw new SnapshotFormatException("Элемент children прогресса parallel должен быть объектом, "
                        + "а получено " + child);
            }
            ScriptStatus status = parseChildStatus(child);
            String reason = optionalText(child, "reason");
            JsonNode grandchild = child.get("child");
            TaskProgress parsed = grandchild == null || grandchild.isNull() ? null : parseProgress(grandchild);
            children.add(new ParallelChild(status, reason, parsed));
        }
        return children;
    }

    private static ScriptStatus parseChildStatus(JsonNode child) {
        String statusText = requireText(child, "status", "элемента children прогресса parallel");
        try {
            return ScriptStatus.valueOf(statusText);
        } catch (IllegalArgumentException e) {
            throw new SnapshotFormatException("Неизвестный статус потомка parallel '" + statusText
                    + "': допустимы RUNNING, SUCCEEDED, FAILED", e);
        }
    }

    private static ConditionBranch parseBranch(JsonNode node) {
        String branch = requireText(node, "branch", "прогресса condition");
        return switch (branch) {
            case "unresolved" -> ConditionBranch.UNRESOLVED;
            case "then" -> ConditionBranch.THEN;
            case "else" -> ConditionBranch.ELSE;
            default -> throw new SnapshotFormatException("Неизвестная ветка прогресса condition '" + branch
                    + "': допустимы unresolved, then, else");
        };
    }

    private static TaskProgress optionalChild(JsonNode node, String owner) {
        JsonNode child = node.get("child");
        return child == null || child.isNull() ? null : parseProgress(child);
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static int requireIndex(JsonNode node, String owner) {
        JsonNode indexNode = node.get("index");
        if (indexNode == null || !indexNode.isIntegralNumber() || !indexNode.canConvertToInt()) {
            throw new SnapshotFormatException("У прогресса '" + owner
                    + "' поле 'index' обязательно и должно быть целым числом в диапазоне int");
        }
        int index = indexNode.intValue();
        if (index < 0) {
            throw new SnapshotFormatException("У прогресса '" + owner + "' отрицательный index=" + index
                    + ": позиция шага нумеруется с нуля");
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

    private static Map<String, Object> parseState(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw new SnapshotFormatException("Состояние скрипта должно быть JSON-объектом, а получено " + node);
        }
        Map<String, Object> state = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> state.put(entry.getKey(), parseValue(entry.getValue(), entry.getKey())));
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
            node.fields().forEachRemaining(
                    entry -> values.put(entry.getKey(), parseValue(entry.getValue(), path + "." + entry.getKey())));
            return values;
        }
        throw new SnapshotFormatException("Состояние скрипта содержит недопустимое значение в '" + path
                + "' (" + node.getNodeType() + "): состояние хранит только JSON-совместимые значения");
    }

    private static void checkAgainstPlan(TaskNode plan, TaskProgress progress) {
        switch (plan) {
            case ScriptCall call -> requireProgress(progress, ScriptAt.class, "script '" + call.script() + "'");
            case SequenceNode sequence -> checkStepAt("sequence", sequence.steps().size(), sequence.steps(), progress);
            case RepeatNode repeat -> checkRepeat(repeat, progress);
            case FallbackNode fallback -> checkFallback(fallback, progress);
            case ParallelNode parallel -> checkParallel(parallel, progress);
            case ConditionNode condition -> checkCondition(condition, progress);
        }
    }

    private static void checkStepAt(String owner, int size, List<TaskNode> steps, TaskProgress progress) {
        if (!(progress instanceof SequenceAt at)) {
            throw mismatch(owner, progress);
        }
        checkStepIndex(owner, at.index(), size, at.child(), steps);
    }

    private static void checkStepIndex(String owner, int index, int size, TaskProgress child, List<TaskNode> steps) {
        if (index > size) {
            throw outOfRange(owner, index, size);
        }
        if (child == null) {
            return;
        }
        if (index >= size) {
            throw new SnapshotFormatException("Прогресс '" + owner + "' указывает на шаг " + index
                    + ", но у узла всего " + size + " шагов: child висит на несуществующем шаге, снимок повреждён");
        }
        checkAgainstPlan(steps.get(index), child);
    }

    private static void checkRepeat(RepeatNode repeat, TaskProgress progress) {
        if (!(progress instanceof RepeatAt at)) {
            throw mismatch("repeat", progress);
        }
        int size = repeat.times();
        if (at.index() > size) {
            throw outOfRange("repeat", at.index(), size);
        }
        if (at.child() == null) {
            return;
        }
        if (at.index() >= size) {
            throw new SnapshotFormatException("Прогресс repeat указывает на повтор " + at.index()
                    + " из " + size + ": child висит на несуществующем повторе, снимок повреждён");
        }
        checkAgainstPlan(repeat.step(), at.child());
    }

    private static void checkFallback(FallbackNode fallback, TaskProgress progress) {
        if (!(progress instanceof FallbackAt at)) {
            throw mismatch("fallback", progress);
        }
        checkStepIndex("fallback", at.index(), fallback.steps().size(), at.child(), fallback.steps());
    }

    private static void checkParallel(ParallelNode parallel, TaskProgress progress) {
        if (!(progress instanceof ParallelAt at)) {
            throw mismatch("parallel", progress);
        }
        int size = parallel.steps().size();
        if (at.children().size() != size) {
            throw new SnapshotFormatException("Прогресс parallel несёт " + at.children().size() + " потомков, а план — "
                    + size + ": снимок повреждён или собран другой версией плана");
        }
        for (int i = 0; i < size; i++) {
            TaskProgress child = at.children().get(i).child();
            if (child != null) {
                checkAgainstPlan(parallel.steps().get(i), child);
            }
        }
    }

    private static void checkCondition(ConditionNode condition, TaskProgress progress) {
        if (!(progress instanceof ConditionAt at)) {
            throw mismatch("condition", progress);
        }
        switch (at.branch()) {
            case UNRESOLVED -> {
                if (at.child() != null) {
                    throw new SnapshotFormatException("Прогресс condition с веткой unresolved несёт child: "
                            + "ветка ещё не выбрана, значит запущенного потомка быть не может, снимок повреждён");
                }
            }
            case THEN -> requireBranchChild("then", condition.thenStep(), at.child());
            case ELSE -> {
                if (condition.elseStep() == null) {
                    throw new SnapshotFormatException("Прогресс condition с веткой else, но у плана нет ветки else: "
                            + "снимок повреждён или собран другой версией плана");
                }
                requireBranchChild("else", condition.elseStep(), at.child());
            }
        }
    }

    private static void requireBranchChild(String branch, TaskNode step, TaskProgress child) {
        if (child == null) {
            throw new SnapshotFormatException("Прогресс condition с веткой " + branch + " без child: выбранная ветка "
                    + "обязана нести потомка, иначе восстановление не знает, что продолжать");
        }
        checkAgainstPlan(step, child);
    }

    private static void requireProgress(TaskProgress progress, Class<? extends TaskProgress> expected, String owner) {
        if (!expected.isInstance(progress)) {
            throw mismatch(owner, progress);
        }
    }

    private static SnapshotFormatException mismatch(String owner, TaskProgress progress) {
        return new SnapshotFormatException("Прогресс не совпадает по форме с планом: узел " + owner + " ожидает другой "
                + "тип прогресса, а получен " + progress.getClass().getSimpleName()
                + ": снимок повреждён или собран другой версией плана, сверьте plan ↔ progress");
    }

    private static SnapshotFormatException outOfRange(String owner, int index, int size) {
        return new SnapshotFormatException("Прогресс '" + owner + "' ссылается на позицию " + index
                + " при " + size + " шагах плана: снимок повреждён или собран другой версией плана");
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
            case SequenceAt at -> indexedToJson("sequence", at.index(), at.child());
            case RepeatAt at -> indexedToJson("repeat", at.index(), at.child());
            case FallbackAt at -> fallbackToJson(at);
            case ParallelAt at -> parallelToJson(at);
            case ConditionAt at -> conditionToJson(at);
            case ScriptAt at -> scriptToJson(at);
        };
    }

    private static ObjectNode indexedToJson(String type, int index, TaskProgress child) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", type);
        node.put("index", index);
        if (child != null) {
            node.set("child", progressToJson(child));
        }
        return node;
    }

    private static ObjectNode fallbackToJson(FallbackAt at) {
        ObjectNode node = indexedToJson("fallback", at.index(), at.child());
        if (at.lastReason() != null) {
            node.put("lastReason", at.lastReason());
        }
        return node;
    }

    private static ObjectNode parallelToJson(ParallelAt at) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "parallel");
        ArrayNode children = node.putArray("children");
        for (ParallelChild child : at.children()) {
            ObjectNode entry = children.addObject();
            entry.put("status", child.status().name());
            if (child.reason() != null) {
                entry.put("reason", child.reason());
            }
            if (child.child() != null) {
                entry.set("child", progressToJson(child.child()));
            }
        }
        return node;
    }

    private static ObjectNode conditionToJson(ConditionAt at) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "condition");
        node.put("branch", at.branch().name().toLowerCase(Locale.ROOT));
        if (at.child() != null) {
            node.set("child", progressToJson(at.child()));
        }
        return node;
    }

    private static ObjectNode scriptToJson(ScriptAt at) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "script");
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
                + value.getClass().getSimpleName() + ": допустимы строки, числа, булевы значения, списки и карты из них");
    }
}
