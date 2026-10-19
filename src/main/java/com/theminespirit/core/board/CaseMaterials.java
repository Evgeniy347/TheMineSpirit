package com.theminespirit.core.board;

import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Материалы кейса по его плану: проход по шагам собирает предметы, которые должны лежать
 * в запасах до выдачи. Крафт считается из рецепта (ингредиенты на число крафтов), схема —
 * из блоков схемы, постановка блока и добыча — по самому шагу. Имена приводятся к короткому
 * виду, как ключи снимка запасов: гейт сравнивает их со складом один в один.
 */
public final class CaseMaterials {
    private static final String MINECRAFT_PREFIX = "minecraft:";

    private CaseMaterials() {}

    public static Map<String, Integer> need(TaskNode plan, MaterialSource source) {
        Objects.requireNonNull(plan, "План кейса не может быть null: без него неизвестно, что нужно собрать");
        Objects.requireNonNull(source, "Источник материалов не может быть null: без него рецепт не прочитать");
        Map<String, Integer> need = new HashMap<>();
        Deque<TaskNode> queue = new ArrayDeque<>();
        queue.add(plan);
        while (!queue.isEmpty()) {
            switch (queue.poll()) {
                case ScriptCall call -> addCall(need, call, source);
                case SequenceNode sequence -> queue.addAll(sequence.steps());
                case RepeatNode repeat -> queue.add(repeat.step());
                case FallbackNode fallback -> queue.addAll(fallback.steps());
                case ParallelNode parallel -> queue.addAll(parallel.steps());
                case ConditionNode condition -> {
                    queue.add(condition.thenStep());
                    if (condition.elseStep() != null) {
                        queue.add(condition.elseStep());
                    }
                }
                default -> {}
            }
        }
        return Map.copyOf(need);
    }

    private static void addCall(Map<String, Integer> need, ScriptCall call, MaterialSource source) {
        switch (call.script()) {
            case "craft" -> addCraft(need, call, source);
            case "build_schematic" -> addSchematic(need, call, source);
            case "place_block" -> merge(need, normalize(text(call, "block")), 1);
            case "acquire" -> merge(need, normalize(text(call, "item")), integer(call, "count"));
            default -> {}
        }
    }

    private static void addCraft(Map<String, Integer> need, ScriptCall call, MaterialSource source) {
        String item = text(call, "item");
        int count = integer(call, "count");
        int output = source.outputCount(item);
        if (output <= 0) {
            return;
        }
        long crafts = (count + (long) output - 1) / output;
        for (Ingredient ingredient : source.ingredientsFor(item)) {
            long total = ingredient.count() * crafts;
            if (total > 0) {
                merge(need, normalize(ingredient.item()), (int) Math.min(total, Integer.MAX_VALUE));
            }
        }
    }

    private static void addSchematic(Map<String, Integer> need, ScriptCall call, MaterialSource source) {
        source.schematicBlocks(text(call, "file")).forEach((block, count) -> merge(need, normalize(block), count));
    }

    private static void merge(Map<String, Integer> need, String item, int count) {
        if (count > 0) {
            need.merge(item, count, Integer::sum);
        }
    }

    private static String text(ScriptCall call, String param) {
        Object value = call.params().get(param);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Шаг '" + call.script() + "' без параметра '" + param
                    + "': по плану не посчитать материалы — исправьте план кейса");
        }
        return text;
    }

    private static int integer(ScriptCall call, String param) {
        Object value = call.params().get(param);
        if (!(value instanceof Integer number)) {
            throw new IllegalArgumentException("Шаг '" + call.script() + "' без целого параметра '" + param
                    + "': по плану не посчитать материалы — исправьте план кейса");
        }
        return number;
    }

    private static String normalize(String item) {
        return item.startsWith(MINECRAFT_PREFIX) ? item.substring(MINECRAFT_PREFIX.length()) : item;
    }
}
