package com.theminespirit.core.board;

import com.theminespirit.core.building.SchematicActions;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.core.script.BuildSchematicParams;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public final class CaseMaterials {
    private static final String MINECRAFT_PREFIX = "minecraft:";
    private static final String ITEM = "item";
    private static final List<String> FUEL_ALTERNATIVES = List.of("charcoal", "coal");

    private CaseMaterials() {}

    public static Map<String, Integer> need(TaskNode plan, MaterialSource source) {
        return walk(plan, source, null);
    }

    public static Map<String, Integer> craftNeed(TaskNode plan, MaterialSource source, String item) {
        Objects.requireNonNull(item, "Предмет крафта не может быть null: без него не выбрать шаг плана");
        return walk(plan, source, normalize(item));
    }

    private static Map<String, Integer> walk(TaskNode plan, MaterialSource source, @Nullable String craftOnly) {
        Objects.requireNonNull(plan, "План кейса не может быть null: без него неизвестно, что нужно собрать");
        Objects.requireNonNull(source, "Источник материалов не может быть null: без него рецепт не прочитать");
        Map<String, Integer> need = new HashMap<>();
        Map<String, Integer> acquired = new HashMap<>();
        Map<String, Integer> placed = new HashMap<>();
        Map<String, Integer> crafted = new HashMap<>();
        Deque<TaskNode> queue = new ArrayDeque<>();
        queue.add(plan);
        while (!queue.isEmpty()) {
            switch (queue.poll()) {
                case ScriptCall call -> {
                    Taken taken = new Taken(acquired, placed, crafted);
                    if (craftOnly == null) {
                        addCall(need, taken, call, source);
                    } else if ("craft".equals(call.script()) && craftOnly.equals(normalize(text(call, ITEM)))) {
                        addCraft(need, taken, call, source);
                    }
                }
                case SequenceNode sequence -> queue.addAll(sequence.steps());
                case RepeatNode repeat -> queue.add(repeat.step());
                case FallbackNode fallback -> queue.add(primary(fallback, source));
                case ParallelNode parallel -> queue.addAll(parallel.steps());
                case ConditionNode condition -> {
                    queue.add(condition.thenStep());
                    if (condition.elseStep() != null) {
                        queue.add(condition.elseStep());
                    }
                }
            }
        }
        crafted.forEach(
                (item, count) -> placed.computeIfPresent(item, (key, left) -> left > count ? left - count : null));
        placed.forEach((item, count) -> merge(need, item, count));
        acquired.forEach((item, count) -> need.merge(item, count, Math::max));
        return Map.copyOf(need);
    }

    private static TaskNode primary(FallbackNode fallback, MaterialSource source) {
        List<TaskNode> steps = fallback.steps();
        TaskNode first = steps.get(0);
        boolean retriedPlace = first instanceof ScriptCall call && "place_block".equals(call.script());
        if (retriedPlace) {
            return first;
        }
        for (TaskNode step : steps) {
            if (!walk(step, source, null).isEmpty()) {
                return step;
            }
        }
        return first;
    }

    private record Taken(Map<String, Integer> acquired, Map<String, Integer> placed, Map<String, Integer> crafted) {}

    private static void addCall(Map<String, Integer> need, Taken taken, ScriptCall call, MaterialSource source) {
        switch (call.script()) {
            case "craft" -> {
                addCraft(need, taken, call, source);
                merge(taken.crafted(), normalize(text(call, ITEM)), integer(call, "count"));
            }
            case "build_schematic" -> addSchematic(taken.placed(), call, source);
            case "place_block" -> merge(taken.placed(), normalize(withoutState(text(call, "block"))), 1);
            case "fill" -> merge(taken.placed(), normalize(withoutState(text(call, "block"))), volume(call));
            case "acquire" -> merge(taken.acquired(), normalize(text(call, ITEM)), integer(call, "count"));
            default -> {}
        }
    }

    private static void addCraft(Map<String, Integer> need, Taken taken, ScriptCall call, MaterialSource source) {
        String item = text(call, ITEM);
        int count = integer(call, "count");
        int output = source.outputCount(item);
        if (output <= 0) {
            return;
        }
        long crafts = (count + (long) output - 1) / output;
        for (Ingredient ingredient : source.ingredientsFor(item)) {
            long total = ingredient.count() * crafts;
            if (total > 0) {
                String planned = plannedFuel(taken, normalize(ingredient.item()));
                merge(need, planned, (int) Math.min(total, Integer.MAX_VALUE));
            }
        }
    }

    private static String plannedFuel(Taken taken, String ingredient) {
        if (!FUEL_ALTERNATIVES.contains(ingredient)) {
            return ingredient;
        }
        if (taken.acquired().getOrDefault(ingredient, 0) > 0) {
            return ingredient;
        }
        return FUEL_ALTERNATIVES.stream()
                .filter(fuel -> !fuel.equals(ingredient))
                .filter(fuel -> taken.acquired().getOrDefault(fuel, 0) > 0)
                .findFirst()
                .orElse(ingredient);
    }

    private static void addSchematic(Map<String, Integer> placed, ScriptCall call, MaterialSource source) {
        String wood =
                call.params().get(BuildSchematicParams.WOOD) instanceof String species ? species : WoodSpecies.OAK;
        List<String> only = call.params().get(BuildSchematicParams.ONLY) instanceof String blocks && !blocks.isBlank()
                ? Stream.of(blocks.split(",")).map(SchematicBlocks::path).toList()
                : List.of();
        source.schematicBlocks(text(call, "file")).forEach((block, count) -> {
            if (!SchematicActions.isActionPlaced(block)
                    && (only.isEmpty() || only.contains(SchematicBlocks.path(block)))) {
                merge(placed, normalize(WoodSpecies.swap(block, wood)), count);
            }
        });
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

    private static int volume(ScriptCall call) {
        int width = integer(call, "x2") - integer(call, "x1") + 1;
        int height = integer(call, "y2") - integer(call, "y1") + 1;
        int depth = integer(call, "z2") - integer(call, "z1") + 1;
        if (width < 1 || height < 1 || depth < 1) {
            throw new IllegalArgumentException(
                    "Шаг 'fill' с перевёрнутой областью: материалы не посчитать — " + "исправьте план кейса");
        }
        return width * height * depth;
    }

    private static String withoutState(String block) {
        int state = block.indexOf('[');
        return state < 0 ? block : block.substring(0, state);
    }

    private static String normalize(String item) {
        return item.startsWith(MINECRAFT_PREFIX) ? item.substring(MINECRAFT_PREFIX.length()) : item;
    }
}
