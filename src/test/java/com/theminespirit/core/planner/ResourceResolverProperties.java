package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import org.junit.jupiter.api.Tag;

class ResourceResolverProperties {
    private static final int MAX_ITEMS = 12;

    @Property
    void resolve_RandomAcyclicKnowledgeGraph_ReturnsPlanOrThrowsOnlyUnplannableGoal(
            @ForAll @Size(max = MAX_ITEMS) List<Integer> decisions,
            @ForAll @IntRange(min = 1, max = 8) int count) {
        ResourceKnowledge knowledge = acyclic(decisions);
        String root = item(decisions.size());

        if (isObtainable(root, knowledge)) {
            TaskNode plan = ResourceResolver.resolve(root, count, knowledge);

            assertEquals(expectedFinalStep(root, count, knowledge), finalStep(plan),
                    "Резолвер обязан оканчивать план действием, добывающим сам предмет нужным количеством: "
                            + "иначе дух выполнит подготовку и не получит цель. Предмет=" + root
                            + ", количество=" + count);
        } else {
            UnplannableGoalException error = assertThrows(UnplannableGoalException.class,
                    () -> ResourceResolver.resolve(root, count, knowledge),
                    "Для предмета без способа добычи в графе резолвер обязан бросить UnplannableGoalException: "
                            + "молчаливый план без предмета выглядит исполненной целью. Предмет=" + root);

            String named = error.item();
            assertTrue(named != null && !isObtainable(named, knowledge),
                    "Поле item обязано называть предмет, у которого в графе нет способа добычи: "
                            + "иначе отказ маскирует дефект резолвера, а не пробел в знаниях. Предмет: " + named);
        }
    }

    private static TaskNode finalStep(TaskNode plan) {
        TaskNode step = plan;
        while (step instanceof SequenceNode sequence) {
            step = sequence.steps().get(sequence.steps().size() - 1);
        }
        return step;
    }

    private static TaskNode expectedFinalStep(String item, int count, ResourceKnowledge knowledge) {
        if (knowledge.isCraftable(item)) {
            return new ScriptCall("craft", Map.of("item", item, "count", count));
        }
        if (knowledge.isSmeltable(item)) {
            return new ScriptCall("smelt", Map.of("item", item, "count", count));
        }
        return new ScriptCall("gather", Map.of("block", item, "count", count,
                "radius", CatalogLimits.HARVEST_RADIUS_DEFAULT));
    }

    private static boolean isObtainable(String item, ResourceKnowledge knowledge) {
        return isObtainable(item, knowledge, new HashSet<>());
    }

    private static boolean isObtainable(String item, ResourceKnowledge knowledge, Set<String> path) {
        if (!path.add(item)) {
            return false;
        }
        try {
            if (knowledge.isCraftable(item)) {
                return knowledge.ingredientsFor(item).stream()
                        .allMatch(ingredient -> isObtainable(ingredient.item(), knowledge, path));
            }
            if (knowledge.isSmeltable(item)) {
                return isObtainable(knowledge.smeltInputFor(item), knowledge, path);
            }
            return knowledge.isGatherable(item);
        } finally {
            path.remove(item);
        }
    }

    private static ResourceKnowledge acyclic(List<Integer> decisions) {
        Set<String> gatherable = new HashSet<>();
        Map<String, List<Ingredient>> crafts = new LinkedHashMap<>();
        Map<String, String> smelts = new LinkedHashMap<>();
        gatherable.add(item(0));
        for (int i = 1; i <= decisions.size(); i++) {
            switch (Math.floorMod(decisions.get(i - 1), 4)) {
                case 1 -> gatherable.add(item(i));
                case 2 -> smelts.put(item(i), item(i - 1));
                case 3 -> {
                    List<Ingredient> ingredients = new ArrayList<>();
                    ingredients.add(new Ingredient(item(i - 1), 1));
                    if (i >= 2) {
                        ingredients.add(new Ingredient(item(i - 2), 2));
                    }
                    crafts.put(item(i), List.copyOf(ingredients));
                }
                default -> {
                }
            }
        }
        return new StubKnowledge(gatherable, crafts, smelts);
    }

    private static String item(int index) {
        return "item_" + index;
    }
}
