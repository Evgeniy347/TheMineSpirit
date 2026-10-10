package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.theminespirit.core.plan.TaskNode;
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

class ResourceResolverProperties {
    private static final int MAX_ITEMS = 12;

    @Property
    void resolve_RandomAcyclicKnowledgeGraph_ReturnsPlanOrThrowsOnlyUnplannableGoal(
            @ForAll @Size(max = MAX_ITEMS) List<Integer> decisions,
            @ForAll @IntRange(min = 1, max = 8) int count) {
        ResourceKnowledge knowledge = acyclic(decisions);
        String root = item(decisions.size());

        try {
            TaskNode plan = ResourceResolver.resolve(root, count, knowledge);
            assertNotNull(plan,
                    "Резолвер на ациклическом графе обязан вернуть план, а не null: исполнение без плана — "
                            + "тихая потеря цели, движок не сможет начать задачу");
        } catch (UnplannableGoalException expected) {
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